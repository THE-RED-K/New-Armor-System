package dev.newarmorsystem.api;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 质量系统的玩家实体效果：移速减益与击退抗性。
 *
 * <p>效果由穿戴总质量实时派生（{@link PlayerMass} 公式）：
 * <ul>
 *   <li><b>击退抗性</b>：{@code min(1.0, 负重比例 × 击退抗性因子)}（ADDITION 修饰符），
 *       因子默认 2.4、可配置（{@code knockbackResistanceFactor}）。
 *       原版护甲与其它模组护甲的击退抗性加成已被 {@code ItemStackMixin} 拦截无效化，
 *       玩家击退抗性完全由本类唯一供给，无护甲时为 0。
 *       <b>负数接口</b>：本公式恒非负；属性范围默认无下限
 *       （{@code knockbackResistanceNoLowerLimit}，关闭后回退
 *       {@code knockbackResistanceMinAllowed}，默认 -2.0），附属模组可直接为玩家
 *       击退抗性添加负修饰符 —— {@link #updateModifier} 只按本类固定 UUID 更新，
 *       外来修饰符不受影响；原版击退公式 {@code 强度 × (1 - 抗性)} 使负抗性 = 击退更远。</li>
 *   <li><b>移速减益</b>：{@code -负重比例 × 移速减益因子}（MULTIPLY_TOTAL 修饰符），
 *       因子默认 0.6、可配置（{@code speedPenaltyFactor}），满负载时移动速度降至 40%。</li>
 *   <li><b>超重归零</b>：负重比例超过超重系数（{@code player_mass.overloadFactor}，默认 1.0）时，
 *       下列 MULTIPLY_TOTAL 修饰符一律钉成 {@link #ZERO_FACTOR}（超大负数），由属性自身下限落到 0：
 *       <ul>
 *         <li>{@code MOVEMENT_SPEED} —— 陆地推进 {@code 0.216·Ms/s³} 直接读该属性，归零即无法行走/疾跑；</li>
 *         <li>{@code ATTACK_SPEED} —— 防"强行超重站撸"（超重换站桩输出）；</li>
 *         <li>{@code ForgeMod.SWIM_SPEED} —— 原版水中推进 {@code 0.02 + (Ms−0.02)·h/3} 在无深海探索者
 *             （h=0）时是常量、<b>不读移速</b>，属性侧唯一能挂的钩子就是这个乘区。</li>
 *       </ul>
 *       为什么不直接用 {@code -1.0}：见 {@link #ZERO_FACTOR} —— 超大负数不依赖"乘区逐条连乘"的实现细节。
 *       <b>刻意留白</b>：原版空中控制走常量 {@code flyingSpeed}（0.02 / 疾跑 0.026），不读移速，
 *       故跳跃后仍有缓慢漂移；空中有大量阻尼且超重套定位为后期，属于设计上接受的范围。
 *       外力推动、骑乘、传送类位移同样不受影响。
 *       由 {@code feature_toggles.overloadZeroEnabled}（默认开启）门控。</li>
 * </ul>
 *
 * <p>使用 transient 修饰符（不写入 NBT），每 tick 按当前负载重算并仅在实际值
 * 变化时更新，避免无意义重建；双端各自维护同一组修饰符 —— 服务端修饰符不会
 * 自动同步到客户端，而移速在客户端参与移动预测，故两端都要应用以保证表现一致，
 * 击退抗性主要作用于服务端，客户端更新无副作用。
 *
 * @author THEREDK
 */
public final class PlayerMassEffects {

    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("0a3d8c1f-7e9b-4d2a-9c6e-2b5f4a7d8c90");
    private static final UUID SPEED_PENALTY_UUID = UUID.fromString("1b4e9d20-8fab-4e3b-ad7f-3c6a5b8e9d01");
    private static final String KNOCKBACK_RESISTANCE_NAME = "new_armor_system.knockback_resistance";
    private static final String SPEED_PENALTY_NAME = "new_armor_system.speed_penalty";
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("2c5f0e31-90bc-4f4c-be80-4d7b6c9f0e12");
    private static final UUID SWIM_SPEED_UUID = UUID.fromString("3d6a1f42-a1cd-4a5d-cf91-5e8c7d0a1f23");
    private static final String ATTACK_SPEED_NAME = "new_armor_system.overload_attack_speed";
    private static final String SWIM_SPEED_NAME = "new_armor_system.overload_swim_speed";

    /**
     * 超重归零因子：用<b>超大负数</b>而不是 {@code -1.0}。
     *
     * <p>{@code -1.0} 在本模组假定的"乘区逐条连乘"（{@code Π(1 + amountᵢ)}）实现下确实得到 0，
     * 但那依赖这一实现细节：若某个加载器/模组把整个乘区求和后统一乘 {@code 1 + Σ}，
     * 其它模组的 {@code +5.0} 就会把 {@code -1.0} 抵消成 {@code +4}（= 5 倍速）——归零失效。
     * 超大负数在两种实现下都保持吸收态（对任意正加成、甚至 NaN/Infinity 都压向负无穷），
     * 再由属性自身的钳制区间（移速/攻速/游泳速度下限均为 0）落到合法的 0。
     */
    private static final double ZERO_FACTOR = -1000000.0D;

    private PlayerMassEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        // 功能总开关：质量系统关断时，两项派生属性一律归零
        // （updateModifier 收到 0 会移除本类固定 UUID 的修饰符，故关断后不会残留旧值）
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        boolean massOn = !loaded || Config.COMMON.massSystemEnabled.get();
        // 是否供给击退抗性由 PlayerMass 统一判定：必须与 ItemStackMixin 剔除原版抗性的条件一致，
        // 否则会出现"原版抗性被剔除、本模组却不再供给"的净损失
        double knockback = PlayerMass.suppliesKnockbackResistance()
                ? PlayerMass.getKnockbackResistance(player) : 0.0;
        double speedPenalty = massOn && (!loaded || Config.COMMON.speedPenaltyEnabled.get())
                ? PlayerMass.getSpeedPenalty(player) : 0.0;
        // 超重归零：移速 / 攻速 / 游泳推进 三项 MULTIPLY_TOTAL 一律钉成 ZERO_FACTOR（超大负数），
        // 由属性自身下限钳到 0；不依赖"乘区逐条连乘"的实现细节（见 ZERO_FACTOR 注释）。
        // 与 speedPenaltyEnabled / speedPenaltyFactor 相互独立，故默认（因子 0.0）下超重仍有代价。
        boolean overloaded = massOn
                && (!loaded || Config.COMMON.overloadZeroEnabled.get())
                && PlayerMass.isOverloaded(player);
        updateModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_UUID,
                KNOCKBACK_RESISTANCE_NAME, knockback,
                AttributeModifier.Operation.ADDITION);
        updateModifier(player, Attributes.MOVEMENT_SPEED, SPEED_PENALTY_UUID,
                SPEED_PENALTY_NAME, overloaded ? ZERO_FACTOR : -speedPenalty,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        // 攻速归零：防止"强行超重站撸"（用超重换取站桩输出）
        updateModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_UUID,
                ATTACK_SPEED_NAME, overloaded ? ZERO_FACTOR : 0.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        // 游泳推进归零：原版水中加速度 0.02 + (Ms − 0.02)·h/3 在无深海探索者（h=0）时是常量、
        // 不读移速，属性侧唯一可挂的钩子就是 Forge 的 SWIM_SPEED 乘区；
        // 属性不存在时 updateModifier 会因 instance == null 自动跳过，无需额外判断。
        updateModifier(player, ForgeMod.SWIM_SPEED.get(), SWIM_SPEED_UUID,
                SWIM_SPEED_NAME, overloaded ? ZERO_FACTOR : 0.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        syncGravity(player);
    }

    /**
     * 重力联动：配置的 {@code fallGravity}（原版 0.08 格/tick²）同步到玩家
     * {@link ForgeMod#ENTITY_GRAVITY} 属性 —— 摔落公式中 g = fallGravity × 400（≈32 m/s²），
     * 同时玩家原版重力（{@code LivingEntity.travel} 的下落加速度）随之变动，
     * 保证"配置 0.08 即原版手感、配置升高则下落更快"的直观语义。
     * 属性合法区间 [0.0, 1.0] 完全覆盖配置范围（0.01 ~ 1.00）。
     *
     * <p><b>功能总开关</b>：{@code fallDamageFormulaEnabled} 关闭时强制回到原版 0.08 ——
     * 否则此前同步过的非原版重力会残留，摔落公式虽已停用、下落手感却仍被改动。
     */
    private static void syncGravity(Player player) {
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
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

    private static void updateModifier(Player player, Attribute attribute, UUID uuid, String name,
                                       double target, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing == null) {
            if (Math.abs(target) > 1.0E-9D) {
                instance.addTransientModifier(new AttributeModifier(uuid, name, target, operation));
            }
        } else if (existing.getAmount() != target) {
            instance.removeModifier(uuid);
            if (Math.abs(target) > 1.0E-9D) {
                instance.addTransientModifier(new AttributeModifier(uuid, name, target, operation));
            }
        }
    }
}
