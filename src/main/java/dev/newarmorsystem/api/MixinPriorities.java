package dev.newarmorsystem.api;

/**
 * 本模组 Mixin 的优先级常量（单一事实来源）。
 *
 * <p><b>为什么"接管型"mixin 要用低优先级</b>：
 * <ol>
 *   <li>Mixin 按优先级<b>升序</b>应用 —— {@code MixinInfo#compareTo} 的字节码为
 *       {@code return this.priority - other.priority}（同级再按注册顺序 {@code order}），
 *       故优先级越低越早写入目标方法。</li>
 *   <li>一个方法被某个 mixin {@code @Overwrite} 之后，其它模组的注入
 *       （{@code @Inject} / {@code @ModifyVariable} / MixinExtras 的 {@code @WrapOperation} 等）
 *       能否落入该方法体，取决于 {@code InjectionPoint#checkPriority(mergedPriority, myPriority)}：
 *       其字节码等价于 {@code mergedPriority < myPriority} —— <b>注入方的优先级必须严格高于接管方</b>，
 *       否则在 PREINJECT 阶段抛出
 *       {@code InvalidInjectionException: ... cannot inject into ... merged by ... with priority ...}；
 *       由于 {@code injectors.defaultRequire = 1}，这会直接导致游戏启动崩溃。
 *       <p><b>实测教训</b>：Apotheosis 7.4.8 的 {@code ItemStackMixin} 声明
 *       {@code priority=500}，与本模组旧的 {@link #TAKE_OVER}(500) <b>平级</b> ⇒
 *       {@code 500 < 500} 不成立 ⇒ 注入失败、启动崩溃（1.20.1 测试实例实测，
 *       异常文本：{@code @At("INVOKE") on ItemStack::swapDura ... cannot inject into
 *       ItemStack::m_220157_ ... merged by dev.newarmorsystem.mixin.ItemStackMixin with priority 500}）。</li>
 *   <li>反过来，{@code @Overwrite} 之间的冲突是"<b>先写入者胜出</b>"：后写入者被
 *       {@code Method overwrite conflict ... Skipping method} 跳过。因此低优先级同时保证
 *       本模组的接管不会被别的 {@code @Overwrite} 取代 —— 两头都稳。</li>
 * </ol>
 *
 * <p><b>分级策略（2026-10-21 起）</b>：接管型 mixin 分两档。
 * <ul>
 *   <li><b>两大核心公式</b>（不可关闭，见 {@code Config} 的说明）用 {@link #TAKE_OVER_FINAL}
 *       （{@code -Integer.MAX_VALUE}）—— 必须<b>最先写入</b>，任何模组都不能取代或早于它们；
 *       其它模组的注入仍可叠加其上（注入方优先级只需严格大于该值，恒成立）。
 *     <ul>
 *       <li>1.20.1：{@code CombatRulesMixin#getDamageAfterAbsorb}（护甲减伤公式）、
 *           {@code InventoryMixin#hurtArmor}（护甲耐久损耗公式）</li>
 *       <li>1.21.1：{@code CombatRulesMixin#getDamageAfterAbsorb}（护甲减伤公式）——
 *           耐久损耗公式在该版已是 {@code LivingEntityHurtEquipmentMixin} 的 {@code @Inject}
 *           （非接管），不占用本档</li>
 *     </ul></li>
 *   <li><b>非核心接管</b>用 {@link #TAKE_OVER_OVER}（250，比默认值低一级）。
 *     <ul>
 *       <li>1.20.1：<b>已无</b> —— {@code ItemStackMixin#hurt}（耐久附魔重做）已于 2026-10-21
 *           重构为三个注入器（{@code @ModifyExpressionValue} + {@code @ModifyArg} +
 *           {@code @ModifyReturnValue}，MixinExtras 0.5.5），原版方法体完整保留、
 *           外部注入锚点全部存活 ⇒ 不再需要靠压优先级回避 Apotheosis 的平级 500
 *           （那类冲突的正确解法是"恢复原版锚点"而非"抢层次"）；该类现按本文件惯例保持默认 1000。</li>
 *       <li>1.21.1：无（该版 {@code ItemStackMixin} 已无接管方法）</li>
 *     </ul></li>
 * </ul>
 *
 * <p><b>使用范围</b>：优先级常量只用于"整体接管方法体"的 {@code @Overwrite} mixin
 * （本版为 {@code CombatRulesMixin}、{@code InventoryMixin}；{@code ItemStackMixin#hurt}
 * 已于 2026-10-21 注入化，不再占用本常量）。
 * 只做注入 / 重定向的 mixin 一律保持默认优先级 1000 —— 若把它们也压低，本模组自己的注入会变成
 * "底层"，反而可能被模组后写入的 {@code @Overwrite} 冲掉。
 *
 * <p>Mixin 默认优先级是 1000，生态中绝大多数 mixin 配置都使用默认值（实测某大型整合包 793 个配置：
 * 792 个 1000、1 个 {@code Integer.MAX_VALUE}，无一低于 1000）—— 这是"低于默认值即可让外部注入叠加"
 * 的实测依据。若某个整合包存在优先级 ≤ 本模组接管值的模组仍冲突，按 {@link #TAKE_OVER_OVER_OVER}、
 * {@link #TAKE_OVER_OVER_OVER_OVER} … 继续下调即可，下面的常量阶梯就是为此准备的。
 *
 * @author THEREDK
 */
public final class MixinPriorities {

    /**
     * 接管型（{@code @Overwrite}）mixin 的优先级：低于 Mixin 默认值 1000，
     * 确保其它模组的注入仍可落入本模组接管后的方法体，同时本模组的接管不被取代。
     */
    public static final int TAKE_OVER = 500;

    public static final int TAKE_OVER_OVER = 250;

    public static final int TAKE_OVER_OVER_OVER = 0;

    public static final int TAKE_OVER_OVER_OVER_OVER = -250;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER = -500;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER_OVER = -750;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER_OVER_OVER = -1000;

    public static final int TAKE_OVER_FINAL = -Integer.MAX_VALUE;

    public static final int WRAPPER = 750;

    /**
     * 最高优先级（最后应用）：供"必须最后定案"的守卫类使用。
     *
     * <p>目前唯一使用者是 {@code mixin.ItemStackZeroLossGuardMixin} —— 它在 {@code ItemStack#hurt}
     * 的 HEAD 处取消式注入，保证本模组公式把耐久损耗压到 0 时一定返回 {@code false}，
     * 不被任何第三方对 {@code hurt} 返回值的改写盖掉（取消式注入让原版方法体根本不执行，
     * 因此该不变量与优先级解耦；取 MAX_VALUE 只是为了在其它 HEAD 取消式注入之前先手）。
     */
    public static final int FINAL = Integer.MAX_VALUE;

    private MixinPriorities() {
    }
}
