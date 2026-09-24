package dev.newarmorsystem.api;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 摔落伤害公式：D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率。
 *
 * <p><b>参数</b>（{@code fall_damage_formula} 配置分组，均可调整）：
 * <ul>
 *   <li><b>g</b>：重力换算值，{@code g = 重力基准值 × 400}。默认重力基准值 0.08
 *       （原版下落加速度常量，格/tick²），× 400 = 32 m/s²。该值同时由
 *       {@link PlayerMassEffects#onPlayerTick} 同步到玩家 1.21 的原版属性
 *       {@code Attributes.GRAVITY}（1.20.1 的 {@code ForgeMod.ENTITY_GRAVITY} 已随
 *       重力收编为原版属性而移除），原版重力效果随之变动。</li>
 *   <li><b>h</b>：下落高度（格），原版 fallDistance（已含 {@code LivingFallEvent} 的修改）。</li>
 *   <li><b>安全高度</b>：<b>取原版属性 {@link Attributes#SAFE_FALL_DISTANCE}</b>（玩家默认 3.0），
 *       而非模组自有变量 —— 这是 1.21 的属性化改造，目的是与其它模组（以及狐狸 5 / 马 6
 *       等原版生物）对安全高度的修改天然兼容。
 *       跳跃提升对该属性的加成已由 {@code JumpBoostSafeFallDistanceMixin}
 *       从"每级 +1 格"改写为"乘算"，即
 *       {@code 安全高度 = 基础值 × (1 + fallJumpSafeGrowth × 等级)}，
 *       增幅系数 {@code fallJumpSafeGrowth} 可配置（默认 0.2 即每级 +20%，配置 0 则无加成）。
 *       若需要像旧版那样固定安全高度，把配置项 {@code fallSafeHeight} 设为 {@code >= 0} 即可，
 *       它会覆写该属性的基础值（默认 {@code -1} = 不覆写，完全跟随属性）。</li>
 *   <li><b>倍率</b>：原版 {@code calculateFallDamage} 的 damageMultiplier 参数
 *       （原版 {@code Block.fallOn} 恒传 1.0；模组可通过
 *       {@code LivingFallEvent.setDamageMultiplier} 修改），纳入公式以保持兼容。</li>
 *   <li><b>分母</b>：默认 1920（0 ~ 100000）。1920 = 60 × 32；非法值（≤ 0）回退 1920，避免除零。</li>
 * </ul>
 *
 * <p><b>设计意图验证</b>：原版环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）、无护甲、
 * 无跳跃提升、倍率 1、安全高度 3 时，D = (60 + 0) × 32 × (h − 3) / 1920 × 1 = h − 3，
 * 与原版完全一致；随护甲质量增长，摔落伤害线性放大。
 *
 * <p><b>事件出口</b>：公式结算完成后派发 {@link PlayerFallDamageEvent}
 * （监听者可改写最终伤害），返回值取事件的 {@code getDamage()}；
 * 无监听者时行为与不派发完全一致。
 *
 * @author THEREDK
 */
public final class FallDamage {

    /** 重力换算系数：0.08 格/tick² × 400 = 32 m/s²（1格 ≈ 1m）。 */
    private static final double GRAVITY_BLOCKS_TO_MPS2 = 400.0;

    /** 分母默认值（设计意图 60 × 32 = 1920）。 */
    private static final int DENOMINATOR_DEFAULT = 1920;

    /** 配置未加载时的内置回退值（与原版一致）。 */
    private static final double GRAVITY_DEFAULT = 0.08;

    private FallDamage() {
    }

    /**
     * 按质量系统公式计算摔落伤害（向上取整，返回生命点数）。
     *
     * <p>公式结算完成后派发 {@link PlayerFallDamageEvent}，返回值取事件改写后的
     * 伤害；无监听者时返回公式结果的向上取整值，与不派发事件完全一致。
     * 安全高度以内（公式结果 0）同样派发。
     *
     * @param player     承受伤害的玩家（质量公式的持有者）
     * @param height     下落高度（格），原版 fallDistance（已含事件修改）
     * @param multiplier 伤害倍率（原版 damageMultiplier 参数，通常为 1.0）
     * @return 摔落伤害点数；高度不超过有效安全高度时为 0
     */
    public static int computeDamage(Player player, float height, float multiplier) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double gravity = loaded ? Config.COMMON.fallGravity.get() : GRAVITY_DEFAULT;
        int denominator = loaded ? Config.COMMON.fallDamageDenominator.get() : DENOMINATOR_DEFAULT;
        if (denominator <= 0) {
            denominator = DENOMINATOR_DEFAULT;
        }

        // 安全高度取原版属性 SAFE_FALL_DISTANCE（默认 3.0，狐狸 5、马 6）：
        // 跳跃提升的加成已由 JumpBoostSafeFallDistanceMixin 改写为乘算并写入该属性，
        // 故此处不再自行处理跳跃提升，也不读模组自身的"安全高度"配置 —— 与其它模组/原版生物
        // 对安全高度的修改天然兼容。
        double safeHeight = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);

        // 总质量：玩家质量 + 穿戴物品总质量（安全区内也算好，随事件携带给监听者）
        double mass = PlayerMass.getPlayerMass(player) + PlayerMass.getTotalWornMass(player);
        int damage;
        if (height <= safeHeight) {
            damage = 0;
        } else {
            double g = gravity * GRAVITY_BLOCKS_TO_MPS2;
            damage = (int) Math.ceil(mass * g * (height - safeHeight) / denominator * multiplier);
        }
        // 派发摔落结算事件：监听者可改写最终伤害；无监听者时行为与不派发完全一致
        PlayerFallDamageEvent event = new PlayerFallDamageEvent(player, height, multiplier, mass, safeHeight, damage);
        NeoForge.EVENT_BUS.post(event);
        return event.getDamage();
    }
}
