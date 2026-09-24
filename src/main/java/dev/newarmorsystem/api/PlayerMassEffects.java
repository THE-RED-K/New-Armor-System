package dev.newarmorsystem.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 质量系统的玩家实体效果：移速减益与击退抗性。
 *
 * <p>效果由穿戴总质量实时派生（{@link PlayerMass} 公式）：
 * <ul>
 *   <li><b>击退抗性</b>：{@code min(1.0, 负重比例 × 击退抗性因子)}（{@code ADD_VALUE} 修饰符）；</li>
 *   <li><b>移速减益</b>：{@code -负重比例 × 移速减益因子}（{@code ADD_MULTIPLIED_TOTAL} 修饰符）。</li>
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

    private PlayerMassEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        updateModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_ID,
                PlayerMass.getKnockbackResistance(player),
                AttributeModifier.Operation.ADD_VALUE);
        updateModifier(player, Attributes.MOVEMENT_SPEED, SPEED_PENALTY_ID,
                -PlayerMass.getSpeedPenalty(player),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        syncGravity(player);
        syncSafeFallDistance(player);
    }

    /**
     * 重力联动：配置的 {@code fallGravity}（原版 0.08 格/tick²）同步到玩家
     * {@link Attributes#GRAVITY} 属性 —— 摔落公式中 g = fallGravity × 400（≈32 m/s²），
     * 同时玩家原版重力（{@code LivingEntity.travel} 的下落加速度）随之变动，
     * 保证"配置 0.08 即原版手感、配置升高则下落更快"的直观语义。
     */
    private static void syncGravity(Player player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        double config = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.fallGravity.get() : 0.08;
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
