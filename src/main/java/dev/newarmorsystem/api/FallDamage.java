package dev.newarmorsystem.api;

import dev.newarmorsystem.compat.PortLibCompat;
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
 *   <li><b>安全高度</b>：{@code -1}（<b>默认</b>）= <b>跟随平台</b> —— 优先取 1.21 属性的
 *       1.20.1 移植 {@code safe_fall_distance}（由 {@code portlib} 一类移植库注册，见
 *       {@link dev.newarmorsystem.compat.PortLibCompat}；其默认值即原版 3 格，
 *       气球/马蹄铁一类饰品的加成因此照旧生效），未装移植库时回退原版硬编码的 3 格；
 *       {@code >= 0} = 显式覆写为配置值（0 ~ 100），h ≤ 安全高度不产生伤害。
 *       与 1.21.1 的同一配置项<b>同语义</b>（那边 {@code -1} = 跟随原版属性
 *       {@code SAFE_FALL_DISTANCE}），故两侧配置文件可双向直接复用。
 *       跳跃提升（{@code MobEffects#JUMP}）改为<b>乘算</b>提升安全高度：
 *       {@code 安全高度 = 基准值 × (1 + fallJumpSafeGrowth × 等级)}，等级 = amplifier + 1；
 *       增幅系数 {@code fallJumpSafeGrowth} 可配置（0.0 ~ 100.0，默认 0.2 即每级 +20%），
 *       配置 0 时跳跃提升对安全高度无影响，无跳跃提升时等级为 0，安全高度即基准值。</li>
 *   <li><b>倍率</b>：原版 {@code calculateFallDamage} 的 damageMultiplier 参数
 *       （原版 {@code Block.fallOn} 恒传 1.0；模组可通过
 *       {@code LivingFallEvent.setDamageMultiplier} 修改），纳入公式以保持兼容。</li>
 *   <li><b>属性倍率</b>：<b>两份相乘</b> ——
 *       ① 本模组自定义属性 {@code new_armor_system:fall_damage_multiplier}
 *       （见 {@link ModAttributes}，默认 1.0，0 = 免疫摔落伤害）；
 *       ② 1.21 属性的 1.20.1 移植 {@code fall_damage_multiplier}
 *       （由 {@code portlib} 一类移植库注册，见 {@link dev.newarmorsystem.compat.PortLibCompat}，
 *       默认 1.0；移植库<b>自己的 mixin 需禁用</b>，应用唯一由本模组完成）。
 *       两者都是乘性、且 0 = 免疫（{@code 0 × x = 0}），任一来源归零都能免除摔落伤害。
 *       于是"某双靴子减免摔落伤害"这类按玩家生效的调整照旧表达，
 *       且它同时作用于整数闸门与 {@code hurt} 处的伤害，故倍率 0 时连摔落音效都不会播（同原版）。</li>
 *   <li><b>分母</b>：默认 1920（0 ~ 100000）。1920 = 60 × 32，即"原版最大生命值环境的
 *       玩家质量 60 × g 32"；非法值（≤ 0）回退 1920，避免除零。</li>
 * </ul>
 *
 * <p><b>设计意图验证</b>：原版环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）、无护甲
 * （护甲质量 0）、无跳跃提升、倍率 1 时，安全高度 = 3，
 * D = (60 + 0) × 32 × (h − 3) / 1920 × 1 = h − 3，
 * 与原版 {@code ceil((fallDistance − 3) × 1)} 一致（越界高度为整数格时完全相同）；
 * 随护甲质量（质量系统）增长，摔落伤害线性放大 —— 重甲玩家在下落场景承受更高
 * 风险，与"质量越大惯性越大"的世界观自洽。跳跃提升则以乘算方式拓宽安全区，
 * 而非原版的按级减 1 格。
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
    /** 原版硬编码的安全高度：1.20.1 的 {@code calculateFallDamage} 里是常量 3。 */
    private static final int SAFE_HEIGHT_DEFAULT = 3;
    private static final double JUMP_SAFE_GROWTH_DEFAULT = 0.2;

    /**
     * 安全高度配置的「跟随原版」哨兵值 —— 与 1.21.1 的同一配置项<b>同语义</b>：
     * {@code -1} = 跟随平台原版，{@code >= 0} = 显式覆写。
     *
     * <p>1.20.1 没有安全高度属性（原版把它硬编码在 {@code calculateFallDamage} 里），
     * 故"跟随原版"解析为 {@link #SAFE_HEIGHT_DEFAULT}（3 格）；1.21.1 则解析为跟随原版属性
     * {@code SAFE_FALL_DISTANCE}。两版配置语义一致，配置文件因此可双向直接复用
     * （旧文件里显式写着的 {@code 3} 仍然表示"覆写为 3"，行为不变）。
     */
    private static final int SAFE_HEIGHT_FOLLOW = -1;

    /** 半格取整容差：远大于该量级的 double 误差（ulp ≈ 1e-14），又远小于半格 0.5。 */
    private static final double DAMAGE_EPSILON = 1.0E-6;

    private FallDamage() {
    }

    /**
     * 摔落伤害公式功能总开关（{@code feature_toggles.fallDamageFormulaEnabled}，默认开启）。
     *
     * <p>关闭时 {@code LivingEntityFallDamageMixin} 不再接管 {@code calculateFallDamage}，
     * 玩家使用原版摔落伤害；同时 {@code PlayerMassEffects#syncGravity}（同类内的私有方法）
     * 会把重力强制回原版 0.08（避免此前同步过的非原版重力残留）。
     * 配置未加载时返回 {@code true}（与默认值一致）。
     *
     * @return 是否使用本模组的摔落伤害公式
     */
    public static boolean isFormulaEnabled() {
        return !Config.COMMON_SPEC.isLoaded() || Config.COMMON.fallDamageFormulaEnabled.get();
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
     * @return 半格取整后的摔落伤害；未越过有效安全高度（含跳跃提升加成）时为 0
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
     * @return 摔落伤害；未越过有效安全高度（含跳跃提升加成）时为 0
     */
    public static double computeDamage(Player player, float height, float multiplier) {
        Computed computed = compute(player, height, multiplier);
        PlayerFallDamageEvent event = new PlayerFallDamageEvent(player, height, multiplier,
                computed.mass(), computed.safeHeight(), computed.damage());
        MinecraftForge.EVENT_BUS.post(event);
        return event.getDamage();
    }

    /** 公式主体（纯读取 + 计算，无副作用，不派发事件）。 */
    private static Computed compute(Player player, float height, float multiplier) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double gravity = loaded ? Config.COMMON.fallGravity.get() : GRAVITY_DEFAULT;
        // 安全高度：-1 = 跟随原版（1.20.1 无安全高度属性，解析为原版硬编码的 3 格），
        // >= 0 = 显式覆写。与 1.21.1 的同一配置项同语义（都 = 跟随平台原版），
        // 因此两侧配置文件可双向直接复用。
        int safeConfig = loaded ? Config.COMMON.fallSafeHeight.get() : SAFE_HEIGHT_FOLLOW;
        // -1 = 跟随平台：优先移植库（portlib 等）注册的 1.21 属性 safe_fall_distance —— 其 mixin
        // 被禁用后属性依然存在、其它模组写入的修饰符照旧保留，只是没人应用，正好由本模组补上；
        // 未装移植库时回退原版硬编码 3 格。>= 0 = NAS 显式覆写优先（保持"覆写"语义）。
        double safeBase = safeConfig >= 0 ? safeConfig
                : PortLibCompat.safeFallDistance(player, SAFE_HEIGHT_DEFAULT);
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
        double safeEffective = safeBase * (1.0 + jumpGrowth * jumpLevel);
        // 总质量：玩家质量 + 穿戴物品总质量（半格精确，见 PlayerMass#getTotalWornMassExact）
        double mass = PlayerMass.getPlayerMass(player) + PlayerMass.getTotalWornMassExact(player);
        double damage = 0.0;
        if (height > safeEffective) {
            double g = gravity * GRAVITY_BLOCKS_TO_MPS2;
            // 属性倍率：两份相乘（各默认 1.0，0 = 免疫摔落伤害）——
            // ① 本模组自定义属性；② 移植库（portlib 等）注册的 1.21 属性 fall_damage_multiplier。
            // 移植库的 mixin 必须禁用（否则它的"应用"与本模组重复），应用唯一由本模组完成，
            // 于是既不会"乘两次"也不会"被覆盖"；属性缺失时两边都返回 1.0，不影响结果。
            double attributeMultiplier = ModAttributes.fallDamageMultiplier(player)
                    * PortLibCompat.fallDamageMultiplier(player);
            damage = quantizeDamage(
                    mass * g * (height - safeEffective) / denominator * multiplier * attributeMultiplier);
        }
        // 注意：height ≤ safeEffective 时 damage 恒为 0，但 mass / safeEffective 仍照常算出并随事件携带
        return new Computed(damage, mass, safeEffective);
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
