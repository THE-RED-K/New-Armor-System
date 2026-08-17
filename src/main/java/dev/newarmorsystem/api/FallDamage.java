package dev.newarmorsystem.api;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/**
 * 摔落伤害公式：D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率。
 *
 * <p><b>参数</b>（{@code fall_damage_formula} 配置分组，均可调整）：
 * <ul>
 *   <li><b>g</b>：重力换算值，{@code g = 重力基准值 × 400}。默认重力基准值 0.08
 *       （原版 {@code LivingEntity.travel} 中的重力常量，格/tick²），× 400 = 32 m/s²，
 *       与现实物理"1格 ≈ 1m"弱相关。只配置重力基准值本身（0.01 ~ 1.00），
 *       该值同时由 {@link PlayerMassEffects} 同步到玩家
 *       {@code ForgeMod.ENTITY_GRAVITY} 属性，原版重力效果随之变动。</li>
 *   <li><b>h</b>：下落高度（格），原版 fallDistance（已含 {@code LivingFallEvent} 的修改）。</li>
 *   <li><b>安全高度</b>：默认 3 格（0 ~ 100），h ≤ 安全高度不产生伤害。跳跃提升
 *       （{@code MobEffects#JUMP}）改为<b>乘算</b>提升安全高度：
 *       {@code 安全高度 = safeHeight × (1 + fallJumpSafeGrowth × 等级)}，等级 = amplifier + 1；
 *       增幅系数 {@code fallJumpSafeGrowth} 可配置（0.0 ~ 100.0，默认 0.2 即每级 +20%），
 *       配置 0 时跳跃提升对安全高度无影响，无跳跃提升时等级为 0，安全高度即配置值。</li>
 *   <li><b>倍率</b>：原版 {@code calculateFallDamage} 的 damageMultiplier 参数
 *       （原版 {@code Block.fallOn} 恒传 1.0；模组可通过
 *       {@code LivingFallEvent.setDamageMultiplier} 修改），纳入公式以保持兼容。</li>
 *   <li><b>分母</b>：默认 1920（0 ~ 100000）。1920 = 60 × 32，即"原版最大生命值环境的
 *       玩家质量 60 × g 32"；非法值（≤ 0）回退 1920，避免除零。</li>
 * </ul>
 *
 * <p><b>设计意图验证</b>：原版环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）、无护甲
 * （护甲质量 0）、无跳跃提升、倍率 1 时，安全高度 = 3，
 * D = (60 + 0) × 32 × (h − 3) / 1920 × 1 = h − 3，
 * 与原版 {@code ceil((fallDistance − 3) × 1)} 完全一致；
 * 随护甲质量（质量系统）增长，摔落伤害线性放大 —— 重甲玩家在下落场景承受更高
 * 风险，与"质量越大惯性越大"的世界观自洽。跳跃提升则以乘算方式拓宽安全区，
 * 而非原版的按级减 1 格。
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
    private static final int SAFE_HEIGHT_DEFAULT = 3;
    private static final double JUMP_SAFE_GROWTH_DEFAULT = 0.2;

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
     * @return 摔落伤害点数；高度不超过有效安全高度（含跳跃提升加成）时为 0
     */
    public static int computeDamage(Player player, float height, float multiplier) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double gravity = loaded ? Config.COMMON.fallGravity.get() : GRAVITY_DEFAULT;
        int safeHeight = loaded ? Config.COMMON.fallSafeHeight.get() : SAFE_HEIGHT_DEFAULT;
        int denominator = loaded ? Config.COMMON.fallDamageDenominator.get() : DENOMINATOR_DEFAULT;
        double jumpGrowth = loaded ? Config.COMMON.fallJumpSafeGrowth.get() : JUMP_SAFE_GROWTH_DEFAULT;
        if (denominator <= 0) {
            denominator = DENOMINATOR_DEFAULT;
        }
        // 跳跃提升：每级（amplifier + 1）使安全高度乘算 (1 + fallJumpSafeGrowth × 等级)
        int jumpLevel = 0;
        MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
        if (jump != null) {
            jumpLevel = jump.getAmplifier() + 1;
        }
        double safeEffective = safeHeight * (1.0 + jumpGrowth * jumpLevel);
        // 总质量：玩家质量 + 穿戴物品总质量（安全区内也算好，随事件携带给监听者）
        double mass = PlayerMass.getPlayerMass(player) + PlayerMass.getTotalWornMass(player);
        int damage;
        if (height <= safeEffective) {
            damage = 0;
        } else {
            double g = gravity * GRAVITY_BLOCKS_TO_MPS2;
            damage = (int) Math.ceil(mass * g * (height - safeEffective) / denominator * multiplier);
        }
        // 派发摔落结算事件：监听者可改写最终伤害；无监听者时行为与不派发完全一致
        PlayerFallDamageEvent event = new PlayerFallDamageEvent(player, height, multiplier, mass, safeEffective, damage);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getDamage();
    }
}
