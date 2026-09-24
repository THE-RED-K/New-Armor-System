package dev.newarmorsystem.api;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TieredItem;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 工具耐久公式（默认关闭）：
 * <pre>
 *   工具耐久 = ( 护甲耐久基数 ) ^ 指数 × 修正系数
 * </pre>
 *
 * <p><b>1.20.1 → 1.21.1 的落点迁移（本次不用 Mixin）</b>：
 * 1.20.1 把公式注入 {@code Item#getMaxDamage()}（无参）—— 那是当时所有耐久查询的唯一汇聚点。
 * 1.21 起该方法已变为 NeoForge {@code IItemExtension} 的
 * <b>default 方法</b> {@code getMaxDamage(ItemStack)}，{@code Item} 自身并未覆写它，
 * Mixin 无法再对 {@code Item} 注入；同时耐久在 1.21 已<b>由数据组件承载</b>。
 * 因此本类改为与 {@link ArmorDurability} 完全同构的「注册期组件改写」：
 * 在 {@link ModifyDefaultComponentsEvent} 阶段直接写入 {@code DataComponents.MAX_DAMAGE}，
 * 从而<b>不需要任何 Mixin</b>，且耐久条渲染 / tooltip / 损坏判定等全部路径天然一致
 * （它们最终都读同一个组件）。
 *
 * <p><b>时序保证</b>（NeoForge {@code CommonModLoader} 的实际顺序）：
 * 模组构造与注册 → <b>配置加载</b> → {@code FMLCommonSetupEvent} →
 * <b>{@code ModifyDefaultComponentsEvent}</b>。故本阶段读配置是安全的
 * （不像 {@code MobEffects}/{@code Attributes} 的静态初始化那样早于配置加载）。
 *
 * <p><b>与 1.20.1 的行为差异</b>：公式结果被<b>烘焙进组件</b>，因此修改
 * {@code tool_durability_formula} 相关配置需要<b>重启游戏</b>才生效
 * （1.20.1 的 {@code getMaxDamage} 注入是每调用读配置、可热改）。
 * 这与护甲耐久基数配置的行为一致（{@link ArmorDurability} 同样是装配期写入）。
 *
 * <p>耐久基数来源（与护甲体系同源，见 {@link ArmorMaterialRules}），
 * 按 <b>Item 级 &gt; 保持原版</b> 分流，工具自身 {@code getUses()}
 * <b>永不参与</b>：
 * <ul>
 *   <li><b>Item 级（唯一绑定入口，精确到单把工具）</b>：
 *       {@link ItemDurabilityRules#registerToolArmorMaterial} 绑定单把工具到护甲材料后，
 *       基数 = {@link ArmorMaterialRules#effectiveDurabilityBase} —— 与护甲共享同一个 B
 *       （玩家调护甲基数时工具同步联动）；也可用
 *       {@link ItemDurabilityRules#registerToolDurabilityBase} 直接登记数值（最强控制）；
 *       系数用 {@link ItemDurabilityRules#registerToolCoefficient}。原版工具即按此法
 *       绑定（见 {@link CompatRegistration#registerVanillaTools()}，由本类在装配时确保调用）。</li>
 *   <li><b>保持原版</b>：其余无登记的物品维持模组自设耐久；
 *       强制模式（{@code toolDurabilityForceAllMaterials}，默认关闭）开启时，
 *       此类工具会打一次 warn 提示未纳入法则。</li>
 * </ul>
 *
 * <p><b>不提供 Tier 级绑定</b>：Tier 是挖掘/工具等级，会被大量模组复用，按 Tier 绑定
 * 会波及所有复用该等级的物品；只按 {@link Item} 精确绑定，第三方工具完全不受影响。
 *
 * <p>例外（仍为单独设定耐久值，不参与公式）：剪刀、钓鱼竿、打火石等
 * 非 {@link TieredItem} 物品 —— 它们的耐久写死在各自构造器，天然排除。
 *
 * <p><b>第三方绑定时机</b>：需要在装配阶段之前完成，即在模组构造期 / 注册期
 * （{@code RegisterEvent}）调用 {@link ItemDurabilityRules} 的登记接口；
 * 若放到 {@code FMLCommonSetupEvent} 的 {@code enqueueWork} 里则有错过本阶段的风险。
 *
 * @author THEREDK
 */
public final class ToolDurability {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 强制模式下已 warn 过的 Item（去重，避免刷屏）。 */
    private static final Set<Item> WARNED_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());

    private ToolDurability() {
    }

    /**
     * 在默认组件阶段为纳入法则的 {@link TieredItem} 写入 {@code MAX_DAMAGE}。
     *
     * <p>需注册到<b>模组事件总线</b>（该事件实现 {@code IModBusEvent}），
     * 且必须注册在 {@link ArmorDurability#onModifyDefaultComponents} <b>之后</b> ——
     * 自定义材料的耐久基数走的是护甲装配期反推缓存，需要护甲侧先跑完。
     *
     * @param event 默认组件修改事件
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        // 原版工具绑定（幂等）：放在这里而不是 common setup，是为了不与本阶段的时序产生依赖
        CompatRegistration.registerVanillaTools();

        if (!Config.COMMON_SPEC.isLoaded() || !Config.COMMON.toolDurabilityFormulaEnabled.get()) {
            return; // 总开关默认关闭：保持原版工具耐久
        }
        double exponent = Config.COMMON.toolDurabilityExponent.get();
        // 先取快照：事件回调会向内部映射写入，避免边遍历边修改
        List<Item> items = event.getAllItems().toList();
        for (Item item : items) {
            if (!(item instanceof TieredItem)) {
                continue; // 剪刀、钓鱼竿等非 TieredItem：仍为单独设定耐久值（例外）
            }
            Double base = resolveDurabilityBase(item);
            if (base == null) {
                continue; // 无登记：保持原版耐久
            }
            double coefficient = coefficientOf(item);
            double durability = Math.pow(base, exponent) * coefficient;
            // 指数过低（如 0.0）时结果可能 < 1，下限保护为 1（避免不可损坏的 0 耐久物品）
            int value = Math.max(1, (int) Math.round(durability));
            event.modify(item, builder -> builder.set(DataComponents.MAX_DAMAGE, value));
        }
    }

    /**
     * 工具耐久基数（<b>仅</b>来自登记，工具自身 {@code getUses()} 不参与），
     * 优先级：Item 显式登记 &gt; Item 护甲反推 &gt; {@code null}（保持原版）。
     *
     * <p>只认 Item 级绑定（精确到单把工具）：Tier 是挖掘/工具等级，会被大量模组复用，
     * 按 Tier 绑定会波及该等级的全部物品；需要精确控制时用
     * {@link ItemDurabilityRules#registerToolArmorMaterial} 只绑这一把。
     *
     * @param item 待查询的工具物品
     * @return 耐久基数；无登记返回 {@code null}（调用方应保持原版）
     */
    public static Double resolveDurabilityBase(Item item) {
        // 1. Item 级显式登记（最强控制）
        Double registered = ItemDurabilityRules.durabilityBaseOf(item);
        if (registered != null) {
            return registered;
        }
        // 2. Item 级护甲反推（与护甲共享同一个 B）
        for (ArmorMaterial material : ItemDurabilityRules.armorMaterialsOf(item)) {
            double base = ArmorMaterialRules.effectiveDurabilityBase(material);
            if (base > 0) {
                return base;
            }
        }
        // 3. 无登记：保持原版
        if (Config.COMMON_SPEC.isLoaded() && Config.COMMON.toolDurabilityForceAllMaterials.get()) {
            warnUncoveredTool(item);
        }
        return null;
    }

    /**
     * 工具耐久修正系数，优先级：Item 显式登记 &gt; 默认 1.0
     * （原版石工具的 0.25 由 {@link ItemDurabilityRules#registerToolCoefficient} 显式登记）。
     *
     * @param item 待查询的工具物品
     * @return 修正系数（恒 &gt; 0）
     */
    public static double coefficientOf(Item item) {
        Double registered = ItemDurabilityRules.coefficientOf(item);
        return registered != null ? registered : 1.0;
    }

    /** 强制模式下，对无法纳入法则（无 Item 登记）的工具打一次 warn。 */
    private static void warnUncoveredTool(Item item) {
        if (WARNED_ITEMS.add(item)) {
            LOGGER.warn(
                    "[NewArmorSystem] Item {} has no durability registration, cannot derive its durability base from armor. "
                            + "Keeping vanilla durability. Bind it via ItemDurabilityRules.registerToolArmorMaterial(Item, ArmorMaterial) "
                            + "or registerToolDurabilityBase(Item, double) to include it in the tool durability formula.",
                    item);
        }
    }
}
