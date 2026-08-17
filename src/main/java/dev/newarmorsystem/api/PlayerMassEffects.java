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
 *       因子默认 1.2、可配置（{@code knockbackResistanceFactor}）。
 *       原版护甲与其它模组护甲的击退抗性加成已被 {@code ItemStackMixin} 拦截无效化，
 *       玩家击退抗性完全由本类唯一供给，无护甲时为 0。
 *       <b>负数接口</b>：本公式恒非负；属性范围默认无下限
 *       （{@code knockbackResistanceNoLowerLimit}，关闭后回退
 *       {@code knockbackResistanceMinAllowed}，默认 -2.0），附属模组可直接为玩家
 *       击退抗性添加负修饰符 —— {@link #updateModifier} 只按本类固定 UUID 更新，
 *       外来修饰符不受影响；原版击退公式 {@code 强度 × (1 - 抗性)} 使负抗性 = 击退更远。</li>
 *   <li><b>移速减益</b>：{@code -负重比例 × 移速减益因子}（MULTIPLY_TOTAL 修饰符），
 *       因子默认 0.6、可配置（{@code speedPenaltyFactor}），满负载时移动速度降至 40%。</li>
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

    private PlayerMassEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        updateModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_UUID,
                KNOCKBACK_RESISTANCE_NAME, PlayerMass.getKnockbackResistance(player),
                AttributeModifier.Operation.ADDITION);
        updateModifier(player, Attributes.MOVEMENT_SPEED, SPEED_PENALTY_UUID,
                SPEED_PENALTY_NAME, -PlayerMass.getSpeedPenalty(player),
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        syncGravity(player);
    }

    /**
     * 重力联动：配置的 {@code fallGravity}（原版 0.08 格/tick²）同步到玩家
     * {@link ForgeMod#ENTITY_GRAVITY} 属性 —— 摔落公式中 g = fallGravity × 400（≈32 m/s²），
     * 同时玩家原版重力（{@code LivingEntity.travel} 的下落加速度）随之变动，
     * 保证"配置 0.08 即原版手感、配置升高则下落更快"的直观语义。
     * 属性合法区间 [0.0, 1.0] 完全覆盖配置范围（0.01 ~ 1.00）。
     */
    private static void syncGravity(Player player) {
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return;
        }
        double config = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.fallGravity.get() : 0.08;
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
