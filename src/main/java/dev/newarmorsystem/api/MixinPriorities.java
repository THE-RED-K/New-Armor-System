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
 *       （{@code @Inject} / {@code @ModifyVariable} / MixinExtras 的 {@code @WrapWithCondition} 等）
 *       能否落入该方法体，取决于 {@code InjectionPoint#checkPriority(mergedPriority, myPriority)}：
 *       其字节码等价于 {@code mergedPriority < myPriority} —— <b>注入方的优先级必须严格高于接管方</b>，
 *       否则在 PREINJECT 阶段抛出
 *       {@code InvalidInjectionException: ... cannot inject into ... merged by ... with priority ...}；
 *       由于 {@code injectors.defaultRequire = 1}，这会直接导致游戏启动崩溃
 *       （实例：EndingLibrary 2.2 的 {@code advanced.data_expand.component.InventoryMixin} 注入
 *       {@code Inventory#hurtArmor} 内的 {@code ItemStack.hurtAndBreak} 调用）。</li>
 *   <li>反过来，{@code @Overwrite} 之间的冲突是"<b>先写入者胜出</b>"：后写入者被
 *       {@code Method overwrite conflict ... Skipping method} 跳过。因此低优先级同时保证
 *       本模组的接管不会被别的 {@code @Overwrite} 取代 —— 两头都稳。</li>
 * </ol>
 *
 * <p><b>取值 500 的理由</b>：Mixin 默认优先级是 1000，生态中绝大多数 mixin 配置都使用默认值
 * （实测某大型整合包 793 个配置：792 个 1000、1 个 {@code Integer.MAX_VALUE}，无一低于 1000）。
 * 500 低于默认值，可保证这类模组的注入全部能落在本模组接管后的方法体上；又不取更极端的值，
 * 以免本模组的接管层与刻意"最先应用"的 mixin（{@code Integer.MIN_VALUE} 一类）互抢层次。
 *
 * <p><b>使用范围</b>：只用于"整体接管方法体"的 {@code @Overwrite} mixin
 * （{@code CombatRulesMixin}、{@code InventoryMixin}、{@code ItemStackMixin#hurt}、{@code ThornsEnchantmentMixin}）。
 * 只做注入 / 重定向的 mixin 一律保持默认优先级 1000 —— 若把它们也压到 500，本模组自己的注入会变成
 * "底层"，反而可能被其它模组后写入的 {@code @Overwrite} 冲掉。
 *
 * <p>某个整合包若存在优先级 ≤ {@value #TAKEOVER} 的模组仍与本模组接管的方法冲突，下调本常量后
 * 重新构建即可（这是把它做成常量的原因）。
 *
 * @author THEREDK
 */
public final class MixinPriorities {

    /**
     * 接管型（{@code @Overwrite}）mixin 的优先级：低于 Mixin 默认值 1000，
     * 确保其它模组的注入仍可落入本模组接管后的方法体，同时本模组的接管不被取代。
     */
    public static final int TAKEOVER = 500;

    private MixinPriorities() {
    }
}
