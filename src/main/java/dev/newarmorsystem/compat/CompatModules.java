package dev.newarmorsystem.compat;

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
 * <p><b>调用时机</b>：由主类在 {@code FMLCommonSetupEvent} 里经 {@code enqueueWork} 调用
 * （该事件的监听器并行执行，而这些登记表非线程安全，需回到主线程）。
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
}
