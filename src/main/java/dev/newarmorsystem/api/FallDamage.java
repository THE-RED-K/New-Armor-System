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
 *   <li><b>分母</b>：默认 1920（0 ~ 100000）。1920 = 60 × 32，即"原版最大生命值环境的
 *       玩家质量 60 × g 32"；非法值（≤ 0）回退 1920，避免除零。</li>
 * </ul>
 *
 * <p><b>设计意图验证</b>：原版环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）、无护甲、
 * 无跳跃提升、倍率 1、安全高度 3 时，D = (60 + 0) × 32 × (h − 3) / 1920 × 1 = h − 3，
 * 与原版一致（越界高度为整数格时完全相同）；随护甲质量增长，摔落伤害线性放大 ——
 * 重甲玩家在下落场景承受更高风险，与"质量越大惯性越大"的世界观自洽。
 * 跳跃提升则以乘算方式拓宽安全区（1.21.1 由 {@code JumpBoostSafeFallDistanceMixin}
 * 写入 {@link Attributes#SAFE_FALL_DISTANCE}），而非原版的按级减 1 格。
 *
 * <p><b>取整刻度</b>：结果为<b>半格</b>（{@code 3.5} 这类小数保留，与质量系统同刻度），
 * 而非原版的整点 {@code ceil} —— 非整数越界高度因此比原版更平滑，
 * 且总质量半格化带来的精度不会在摔落伤害的出口处被抹掉。
 *
 * <p><b>事件出口</b>：公式结算完成后派发 {@link PlayerFallDamageEvent}
 * （监听者可改写最终伤害），返回值取事件的 {@code getDamage()}；
 * 无监听者时行为与不派发完全一致。两条出口
 * （{@link #computeRawDamage} 不派发事件 / {@link #computeDamage} 派发）共用同一份计算，
 * 由 {@code LivingEntityFallDamageMixin} 分别用于原版整数闸门与 {@code hurt} 伤害。
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

    /** 半格取整容差：远大于该量级的 double 误差（ulp ≈ 1e-14），又远小于半格 0.5。 */
    private static final double DAMAGE_EPSILON = 1.0E-6;

    private FallDamage() {
    }

    /**
     * 公式中间量：最终伤害 + 事件需要携带的两项输入（总质量、有效安全高度）。
     *
     * <p>用 record 承载是为了让"派发事件"与"不派发事件"两条出口共用同一份计算，
     * 避免安全高度/质量的口径在两处漂移。
     */
    private record Computed(double damage, double mass, double safeHeight) {
    }

    /**
     * 摔落伤害（<b>半格取整</b>，<b>不派发事件</b>）。
     *
     * <p>供 {@code LivingEntityFallDamageMixin} 决定原版 {@code i > 0} <b>整数闸门</b>使用 ——
     * 闸门只关心"是否大于 0"，真正的伤害值由 {@link #computeDamage} 在 {@code hurt} 处施加。
     * 两条出口共用 {@link #compute} 的同一份计算，故数值必然一致。
     *
     * @param player     承受伤害的玩家（质量公式的持有者）
     * @param height     下落高度（格），原版 fallDistance（已含事件修改）
     * @param multiplier 伤害倍率（原版 damageMultiplier 参数，通常为 1.0）
     * @return 半格取整后的摔落伤害；未越过有效安全高度时为 0
     */
    public static double computeRawDamage(Player player, float height, float multiplier) {
        return compute(player, height, multiplier).damage();
    }

    /**
     * 摔落伤害（半格取整 + 派发 {@link PlayerFallDamageEvent}）。
     *
     * <p>公式结算完成后派发 {@link PlayerFallDamageEvent}，返回值取事件改写后的伤害；
     * 无监听者时与 {@link #computeRawDamage} 完全一致（半格取整值）。
     *
     * @param player     承受伤害的玩家（质量公式的持有者）
     * @param height     下落高度（格），原版 fallDistance（已含事件修改）
     * @param multiplier 伤害倍率（原版 damageMultiplier 参数，通常为 1.0）
     * @return 摔落伤害；未越过有效安全高度时为 0
     */
    public static double computeDamage(Player player, float height, float multiplier) {
        Computed computed = compute(player, height, multiplier);
        PlayerFallDamageEvent event = new PlayerFallDamageEvent(player, height, multiplier,
                computed.mass(), computed.safeHeight(), computed.damage());
        NeoForge.EVENT_BUS.post(event);
        return event.getDamage();
    }

    /** 公式主体（纯读取 + 计算，无副作用，不派发事件）。 */
    private static Computed compute(Player player, float height, float multiplier) {
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

        // 总质量：玩家质量 + 穿戴物品总质量（半格精确，见 PlayerMass#getTotalWornMassExact）
        double mass = PlayerMass.getPlayerMass(player) + PlayerMass.getTotalWornMassExact(player);
        double damage = 0.0;
        if (height > safeHeight) {
            double g = gravity * GRAVITY_BLOCKS_TO_MPS2;
            damage = quantizeDamage(mass * g * (height - safeHeight) / denominator * multiplier);
        }
        // 注意：height ≤ safeHeight 时 damage 恒为 0，但 mass / safeHeight 仍照常算出并随事件携带
        return new Computed(damage, mass, safeHeight);
    }

    /**
     * 伤害取整：<b>小数部分 &lt; 0.5 → 向下取整；≥ 0.5 → 取 0.5</b>（最小刻度半格），
     * 与质量系统（{@code PlayerMass} 的物品质量/总质量）同一套刻度。
     *
     * <p><b>下限 0.5</b>：只要越过了安全高度（原式为正值）就至少造成半格伤害 ——
     * 否则公式结果落在 (0, 0.5) 时会被抹成 0，出现"明明摔出安全区却毫发无伤"的观感矛盾
     * （原版此处是 {@code ceil}，任何非零越界都造成 1 点伤害）。下限取半格而非 1，
     * 是为了不倒退成原版的整点粒度。
     *
     * @param raw 公式原始结果
     * @return 半格刻度值（原始结果 ≤ 0 时返回 0）
     */
    private static double quantizeDamage(double raw) {
        if (raw <= 0.0) {
            return 0.0;
        }
        return Math.max(0.5, Math.floor(raw * 2.0 + DAMAGE_EPSILON) / 2.0);
    }
}
