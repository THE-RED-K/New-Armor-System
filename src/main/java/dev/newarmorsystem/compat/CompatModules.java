package dev.newarmorsystem.compat;

import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 兼容模块总入口 —— 汇总各模组的适配注册。
 *
 * <p>本包由原独立项目 <b>NAS Compatibility Patch</b> 合并而来。原来那些补丁模块只调用本模组的
 * 公开 API（无 Mixin、无自定义注册内容），其价值完全在于"数据 + 调用顺序"，
 * 因此合并进本模组后可直接内联，无需再让整合包多装一个 jar。
 *
 * <p><b>两类内容的语义区分</b>（合并时按用户要求划开）：
 * <ul>
 *   <li><b>机制适配（自带内容，始终生效）</b>：护甲类型、耐久基数、质量系数、修理系数、
 *       工具绑定 —— 让第三方护甲能被本系统的机制正确识别；</li>
 *   <li><b>数值改写（整合包内容，受门控）</b>：护甲值 / 盔甲韧性 —— 具体数字是某个整合包
 *       为拉开梯度选的，不该由模组替所有玩家决定，故全部改走
 *       {@link CompatArmorAttributes}，由 {@code feature_toggles.experimentalFeaturesEnabled}
 *       这一个实验性总开关控制，默认关闭。</li>
 * </ul>
 *
 * <p>每个模块的 {@code registerAll()} 都以 {@code ModList.isLoaded(modId)} 开头，
 * 未安装对应模组时直接返回；物品与材料一律通过注册表按 ID 运行时获取，
 * 因此<b>没有任何编译期依赖</b>，也不会因某个模组缺失而报错。
 *
 * <p><b>调用时机（1.20.1 → 1.21.1 的关键差异）</b>：1.20.1 由主类在
 * {@code FMLCommonSetupEvent} 里经 {@code enqueueWork} 调用；1.21.1 <b>不能</b>沿用 ——
 * 该版本护甲/工具耐久是在 {@code ModifyDefaultComponentsEvent} 的<b>装配期烘焙</b>进
 * 数据组件的，而 {@code FMLCommonSetupEvent} 早于注册表填充、更早于该事件，
 * 在那里按 ID 查物品会查不到；反之晚于装配期则登记失效。
 * 故 1.21.1 由主类把本类方法注册为<b>装配事件监听器中的第一个</b>
 * （在 {@code ArmorDurability} / {@code ToolDurability} 之前），
 * 与 {@code ToolDurability} 内部调用 {@code CompatRegistration.registerVanillaTools()} 同源。
 *
 * <p><b>移植进度</b>：1.20.1 侧的模组适配模块（暮色森林 / Alex's Mobs / Alex's Caves /
 * 冰火 / 沉浸护甲 / Everything is Copper / Pure Emerald Tools）按批次陆续接入，
 * 每接入一个即在 {@link #registerAll()} 中挂上。
 *
 * @author THEREDK
 */
public final class CompatModules {

    private CompatModules() {
    }

    /**
     * 按模组分批注册全部兼容规则。幂等性由各模块自身的登记语义保证
     * （重复调用只是覆盖同样的值）。
     */
    public static void registerAll() {
        TwilightForestCompat.registerAll();
        EverythingIsCopperCompat.registerAll();
        PureEmeraldToolsCompat.registerAll();
        ImmersiveArmorsCompat.registerAll();
        AlexsMobsCompat.registerAll();
        AlexsCavesCompat.registerAll();
        IceAndFireCompat.registerAll();
        // 原版护甲数值重排：纯"整合包内容"，全部登记都在实验性总开关之后
        VanillaArmorCompat.registerAll();
    }

    /**
     * 装配期入口（注册到<b>模组事件总线</b>，事件实现 {@code IModBusEvent}）。
     *
     * <p>由主类注册为 {@code ModifyDefaultComponentsEvent} 的<b>第一个</b>监听器 ——
     * 必须在 {@link dev.newarmorsystem.api.ArmorDurability} / {@link dev.newarmorsystem.api.ToolDurability}
     * 之前执行，因为那两个类会在此阶段把耐久烘焙进默认组件，用到的正是本类登记的材料规则。
     *
     * <p>本阶段物品注册表已填充（{@code RegisterEvent} 已完成），故按 ID 查第三方物品是安全的；
     * 这也是此处**不能**改用 {@code FMLCommonSetupEvent} 的原因（该阶段注册表尚未填充）。
     *
     * @param event 默认组件修改事件（本方法不读其内容，仅为挂载到装配阶段）
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        registerAll();
    }
}
