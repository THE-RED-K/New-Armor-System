package dev.newarmorsystem.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 质量系统的玩家实体效果：移速减益与击退抗性。
 *
 * <p>效果由穿戴总质量实时派生（{@link PlayerMass} 公式）：
 * <ul>
 *   <li><b>击退抗性</b>：{@code min(1.0, 负重比例 × 击退抗性因子)}（{@code ADD_VALUE} 修饰符）；</li>
 *   <li><b>移速减益</b>：{@code -负重比例 × 移速减益因子}（{@code ADD_MULTIPLIED_TOTAL} 修饰符）。</li>
 *   <li><b>超重归零</b>：负重比例超过超重系数（{@code player_mass.overloadFactor}，默认 1.0）时，
 *       下列 {@code ADD_MULTIPLIED_TOTAL} 修饰符一律钉成 {@link #ZERO_FACTOR}（超大负数），
 *       由属性自身下限落到 0：
 *       <ul>
 *         <li>{@code MOVEMENT_SPEED} —— 陆地推进 {@code 0.216·Ms/s³} 直接读该属性，归零即无法行走/疾跑；</li>
 *         <li>{@code ATTACK_SPEED} —— 防"强行超重站撸"（超重换站桩输出）；</li>
 *         <li>{@code NeoForgeMod.SWIM_SPEED} —— 原版水中推进 {@code 0.02 + (Ms−0.02)·h/3} 在无深海探索者
 *             （h=0）时是常量、<b>不读移速</b>，属性侧唯一能挂的钩子就是这个乘区。</li>
 *       </ul>
 *       为什么不直接用 {@code -1.0}：见 {@link #ZERO_FACTOR} —— 超大负数不依赖"乘区逐条连乘"的实现细节。
 *       <b>刻意留白</b>：原版空中控制走常量 {@code flyingSpeed}（0.02 / 疾跑 0.026），不读移速，
 *       故跳跃后仍有缓慢漂移；空中有大量阻尼且超重套定位为后期，属于设计上接受的范围。
 *       外力推动、骑乘、传送类位移同样不受影响。
 *       由 {@code feature_toggles.overloadZeroEnabled}（默认开启）门控。</li>
 * </ul>
 *
 * <p><b>1.20.1 → 1.21.1 适配</b>：
 * <ul>
 *   <li>{@link AttributeModifier} 由「UUID + 名称」变为 <b>record（{@code ResourceLocation} id + amount + operation）</b>，
 *       故修饰符标识改用 {@link ResourceLocation}；</li>
 *   <li>{@code Operation} 枚举改名：{@code ADDITION → ADD_VALUE}、{@code MULTIPLY_TOTAL → ADD_MULTIPLIED_TOTAL}；</li>
 *   <li>属性改为 {@code Holder<Attribute>}，{@code player.getAttribute(...)} 相应接收 Holder；</li>
 *   <li>tick 事件由 {@code TickEvent.PlayerTickEvent(phase=END)} 变为
 *       {@link PlayerTickEvent.Post}；</li>
 *   <li>重力属性 {@code ForgeMod.ENTITY_GRAVITY} 已被移除 —— 1.21 起 Mojang 把重力收编为原版属性
 *       {@link Attributes#GRAVITY}。</li>
 * </ul>
 *
 * <p>使用 transient 修饰符（不写入 NBT），每 tick 按当前负载重算并仅在实际值
 * 变化时更新；双端各自维护同一组修饰符 —— 服务端修饰符不会自动同步到客户端，
 * 而移速在客户端参与移动预测，故两端都要应用。
 *
 * @author THEREDK
 */
public final class PlayerMassEffects {

    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath("new_armor_system", "knockback_resistance");
    private static final ResourceLocation SPEED_PENALTY_ID =
            ResourceLocation.fromNamespaceAndPath("new_armor_system", "speed_penalty");
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("new_armor_system", "overload_attack_speed");
    private static final ResourceLocation SWIM_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("new_armor_system", "overload_swim_speed");

    /**
     * 超重归零因子：用<b>超大负数</b>而不是 {@code -1.0}。
     *
     * <p>{@code -1.0} 在本模组假定的"乘区逐条连乘"（{@code Π(1 + amountᵢ)}）实现下确实得到 0，
     * 但那依赖这一实现细节：若某个加载器/模组把整个乘区求和后统一乘 {@code 1 + Σ}，
     * 其它模组的 {@code +5.0} 就会把 {@code -1.0} 抵消成 {@code +4}（= 5 倍速）——归零失效。
     * 超大负数在两种实现下都保持吸收态（对任意正加成、甚至 NaN/Infinity 都压向负无穷），
     * 再由属性自身的钳制区间（移速/攻速/游泳速度下限均为 0）落到合法的 0。
     */
    private static final double ZERO_FACTOR = -1_000_000.0D;

    private PlayerMassEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // 功能总开关：质量系统关断时，两项派生属性一律归零
        // （updateModifier 收到 0 会移除本模组固定 ID 的修饰符，故关断后不会残留旧值）
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        boolean massOn = !loaded || Config.COMMON.massSystemEnabled.get();
        // 是否供给击退抗性由 PlayerMass 统一判定：必须与 ItemStackMixin 剔除原版抗性的条件一致，
        // 否则会出现"原版抗性被剔除、本模组却不再供给"的净损失
        double knockback = PlayerMass.suppliesKnockbackResistance()
                ? PlayerMass.getKnockbackResistance(player) : 0.0;
        double speedPenalty = massOn && (!loaded || Config.COMMON.speedPenaltyEnabled.get())
                ? PlayerMass.getSpeedPenalty(player) : 0.0;
        // 超重归零：移速 / 攻速 / 游泳推进 三项 ADD_MULTIPLIED_TOTAL 一律钉成 ZERO_FACTOR（超大负数），
        // 由属性自身下限钳到 0；不依赖"乘区逐条连乘"的实现细节（见 ZERO_FACTOR 注释）。
        // 与 speedPenaltyEnabled / speedPenaltyFactor 相互独立，故默认（因子 0.0）下超重仍有代价。
        boolean overloaded = massOn
                && (!loaded || Config.COMMON.overloadZeroEnabled.get())
                && PlayerMass.isOverloaded(player);
        updateModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_ID,
                knockback,
                AttributeModifier.Operation.ADD_VALUE);
        updateModifier(player, Attributes.MOVEMENT_SPEED, SPEED_PENALTY_ID,
                overloaded ? ZERO_FACTOR : -speedPenalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // 攻速归零：防止"强行超重站撸"（用超重换取站桩输出）
        updateModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID,
                overloaded ? ZERO_FACTOR : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // 游泳推进归零：原版水中加速度 0.02 + (Ms − 0.02)·h/3 在无深海探索者（h=0）时是常量、
        // 不读移速，属性侧唯一可挂的钩子就是 NeoForge 的 SWIM_SPEED 乘区；
        // 属性不存在时 updateModifier 会因 instance == null 自动跳过，无需额外判断。
        updateModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_SPEED_ID,
                overloaded ? ZERO_FACTOR : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        syncGravity(player);
        syncSafeFallDistance(player);
    }

    /**
     * 重力联动：配置的 {@code fallGravity}（原版 0.08 格/tick²）同步到玩家
     * {@link Attributes#GRAVITY} 属性 —— 摔落公式中 g = fallGravity × 400（≈32 m/s²），
     * 同时玩家原版重力（{@code LivingEntity.travel} 的下落加速度）随之变动，
     * 保证"配置 0.08 即原版手感、配置升高则下落更快"的直观语义。
     *
     * <p><b>功能总开关</b>：{@code fallDamageFormulaEnabled} 关闭时强制回到原版 0.08 ——
     * 否则此前同步过的非原版重力会残留，摔落公式虽已停用、下落手感却仍被改动。
     */
    private static void syncGravity(Player player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        boolean formulaOn = !loaded || Config.COMMON.fallDamageFormulaEnabled.get();
        double config = formulaOn && loaded ? Config.COMMON.fallGravity.get() : 0.08;
        if (gravity.getBaseValue() != config) {
            gravity.setBaseValue(config);
        }
    }

    /**
     * 安全下落距离覆写（<b>可选</b>）：{@code fallSafeHeight >= 0} 时把玩家的
     * {@link Attributes#SAFE_FALL_DISTANCE} 基础值强制为该值（单位：格）；默认 {@code -1}
     * 表示<b>不覆写</b>，完全跟随属性本身（原版 3.0，其它模组的修改一并保留）——
     * 这是默认行为，用于兼容其它模组对安全高度的调整。
     *
     * <p>之所以覆盖<b>基础值</b>而不是挂一个修饰符：跳跃提升的乘算加成
     * （{@code ADD_MULTIPLIED_BASE}，见 {@code JumpBoostSafeFallDistanceMixin}）
     * 正是以基础值作为乘算基底，只有改基础值才能得到与 1.20.1 旧配置完全等价的结果
     * {@code fallSafeHeight × (1 + fallJumpSafeGrowth × 等级)}。
     * 同理，覆写后的值对任何读取该属性的模组同样可见。
     *
     * <p><b>注意</b>：属性基础值会随玩家数据写入存档，因此把配置改回 {@code -1}
     * 不会自动还原已覆写的值（与 {@link #syncGravity} 同源的行为）。
     */
    private static void syncSafeFallDistance(Player player) {
        // 功能总开关关闭：不覆写（本覆写属于摔落伤害公式模块；已覆写的基础值不会自动还原，见上方说明）
        if (Config.COMMON_SPEC.isLoaded() && !Config.COMMON.fallDamageFormulaEnabled.get()) {
            return;
        }
        int config = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.fallSafeHeight.get() : -1;
        if (config < 0) {
            return;
        }
        AttributeInstance safeFallDistance = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFallDistance != null && safeFallDistance.getBaseValue() != config) {
            safeFallDistance.setBaseValue(config);
        }
    }

    /**
     * 按固定 ID 更新单个修饰符：值未变时不重建，避免每 tick 无意义重算；
     * 目标值接近 0 时移除修饰符。
     */
    private static void updateModifier(Player player, Holder<Attribute> attribute, ResourceLocation id,
                                       double target, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing == null) {
            if (Math.abs(target) > 1.0E-9D) {
                instance.addTransientModifier(new AttributeModifier(id, target, operation));
            }
        } else if (existing.amount() != target) {
            instance.removeModifier(id);
            if (Math.abs(target) > 1.0E-9D) {
                instance.addTransientModifier(new AttributeModifier(id, target, operation));
            }
        }
    }
}
