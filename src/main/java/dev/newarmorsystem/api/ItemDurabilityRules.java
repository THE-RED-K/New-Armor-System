package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具耐久法则注册接口 —— 将任意 {@link Item} 纳入
 * "工具耐久 = (护甲耐久基数)^指数 × 修正系数" 法则（见 {@link ToolDurability}）。
 *
 * <p><b>仅支持按物品绑定</b>：Tier 是<b>挖掘/工具等级</b>，会被大量模组复用
 * （同一等级的不同工具、甚至完全无关的物品共用同一 Tier），按 Tier 绑定会一次性
 * 波及所有复用该等级的物品（几乎所有添加工具的模组都复用了原版或自定义 Tier）。
 * 故本系统<b>不提供任何 Tier 级登记</b>；一律通过 {@link Item}（具体物品）绑定，
 * 精确到单个工具 —— 只有被绑定的那把剑/镐/斧才纳入法则，同等级的其他物品（包括
 * 第三方模组复用该 Tier 的工具）完全不受影响。
 *
 * <p>工具耐久公式（总开关 {@code tool_durability_formula.toolDurabilityFormulaEnabled}，
 * 默认关闭）的耐久基数分流优先级：
 * <ol>
 *   <li><b>Item 级显式登记基数</b>（{@link #registerToolDurabilityBase}）—— 单把工具直接
 *       登记数值（最强控制，可覆写一切）；</li>
 *   <li><b>Item 级护甲反推</b>（{@link #registerToolArmorMaterial}）—— 单把工具绑定护甲
 *       材料，基数只来自护甲侧，经 {@link ArmorMaterialRules#effectiveDurabilityBase} 反推，
 *       与护甲共享同一个 B（玩家调护甲基数时该工具同步联动）；</li>
 *   <li><b>保持原版</b> —— 其余无登记的物品维持模组自设耐久（强制模式
 *       {@code toolDurabilityForceAllMaterials} 开启时会打 warn 提示未纳入）。</li>
 * </ol>
 *
 * <p>修正系数同理：Item 级（{@link #registerToolCoefficient}）&gt; 默认 1.0
 * （原版石工具的 0.25 由 {@link CompatRegistration#registerVanillaTools()} 显式登记）。
 *
 * <p>系数与护甲绑定<b>正交</b>：有护甲材料的工具同样可配系数 —— 用
 * {@link #registerToolArmorMaterial(Item, ArmorMaterial, double)}（或
 * {@link #registerToolCoefficient}）即可在共享 B 的同时叠加修正系数
 * （{@code D = B^指数 × 系数}），不必为蹭系数而放弃护甲绑定去走显式基数登记。
 *
 * <p><b>登记 API 全貌（语义封闭）</b>：3 个原子操作 + 5 个组合便利重载，
 * 已覆盖"基数来源 × 系数"的全部组合，无需再增加重载：
 * <ul>
 *   <li>原子操作（可任意自由组合）：
 *       {@link #registerToolArmorMaterial(Item, ArmorMaterial)}（绑定护甲材料）、
 *       {@link #registerToolDurabilityBase(Item, double)}（显式基数）、
 *       {@link #registerToolCoefficient(Item, double)}（修正系数）；</li>
 *   <li>护甲绑定（单材料）+ 系数：
 *       {@link #registerToolArmorMaterial(Item, ArmorMaterial, double)}；</li>
 *   <li>护甲绑定（一对多）无系数 / 带系数：
 *       {@link #registerToolArmorMaterials(Item, ArmorMaterial[])} /
 *       {@link #registerToolArmorMaterials(Item, double, ArmorMaterial[])}；</li>
 *   <li>显式基数 + 系数：{@link #registerToolDurabilityBase(Item, double, double)}。</li>
 * </ul>
 * 所有组合重载均为对应原子操作的语法糖（语义完全等价）；系数仅在基数有效时生效。
 *
 * <p>原版工具即通过 Item 级 API 绑定（见
 * {@link CompatRegistration#registerVanillaTools()}，同时充当使用示例）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：键类型不变（仍按护甲材料身份登记）。1.21.1 的
 * {@link ArmorMaterial} 已由枚举变为注册表条目（记录类型），故调用方用
 * {@code armorItem.getMaterial().value()} 取得实例再传入
 * （{@code ArmorMaterials} 的常量同样是 {@code Holder}，需 {@code .value()}）。
 *
 * <p>登记表<b>非线程安全</b>，建议在加载期（<b>模组构造期 / 注册期</b>，
 * 与 {@link DamageReflection#register} 同批）调用 —— 必须早于 {@code ModifyDefaultComponentsEvent}
 * 装配阶段（见 {@link ToolDurability}）。注册值 ≤ 0 视为取消注册（恢复默认行为）。
 *
 * @author THEREDK
 */
public final class ItemDurabilityRules {

    private ItemDurabilityRules() {
    }

    /** Item → 显式登记的耐久基数。 */
    private static final Map<Item, Double> ITEM_BASE = new IdentityHashMap<>();

    /** Item → 显式登记的修正系数。 */
    private static final Map<Item, Double> ITEM_COEFFICIENT = new IdentityHashMap<>();

    /** Item → 绑定的护甲材料集合（精确到单把工具）。 */
    private static final Map<Item, List<ArmorMaterial>> ITEM_ARMOR_MATERIAL = new IdentityHashMap<>();

    // ===== Item 级绑定（精确到单把工具；唯一登记入口）=====

    /**
     * 将<b>单把工具</b>与一个护甲材料 {@link ArmorMaterial} 绑定（推荐用法）。
     *
     * <p>Tier 是挖掘/工具等级，被大量模组复用——按 Tier 绑定会波及该等级的全部物品；
     * 按 {@link Item} 绑定则只有这一把工具纳入法则。绑定后该工具耐久基数<b>只</b>从
     * 护甲侧反推（{@link ArmorMaterialRules#effectiveDurabilityBase}），与护甲共享
     * 同一个 B（工具自身的 {@code getUses} 不参与），玩家调护甲基数时工具同步联动。
     * 原版工具即用此法绑定（见 {@link CompatRegistration#registerVanillaTools()}）。
     *
     * @param item     具体的工具物品（如 {@code Items.IRON_SWORD}）
     * @param material 该工具对应的护甲材料（{@code null} 取消绑定）
     */
    public static void registerToolArmorMaterial(Item item, ArmorMaterial material) {
        if (material != null) {
            registerToolArmorMaterials(item, material);
        } else {
            ITEM_ARMOR_MATERIAL.remove(item);
        }
    }

    /**
     * 将<b>单把工具</b>与护甲材料 {@link ArmorMaterial} 绑定，并同时为其登记修正系数
     * （"绑定护甲材料共享 B + 系数"一步到位）。
     *
     * <p>语义等价于依次调用 {@link #registerToolArmorMaterial(Item, ArmorMaterial)}
     * 与 {@link #registerToolCoefficient(Item, double)}：耐久基数仍从护甲侧反推
     * （与护甲共享同一个 B，工具自身 {@code getUses} 不参与），系数叠加生效
     * （{@code D = B^指数 × 系数}）。适合"同材料不同重量级"的场景——如某把工具
     * 比同材料的其他工具更耐用/更脆（原版石工具的 0.25 即为一例）。
     *
     * @param item        具体的工具物品（如 {@code Items.IRON_SWORD}）
     * @param material    该工具对应的护甲材料（{@code null} 只取消绑定，系数按 {@code coefficient} 处理）
     * @param coefficient 修正系数（> 0 登记；≤ 0 取消，恢复默认 1.0）
     */
    public static void registerToolArmorMaterial(Item item, @Nullable ArmorMaterial material, double coefficient) {
        registerToolArmorMaterial(item, material);
        if (coefficient > 0) {
            ITEM_COEFFICIENT.put(item, coefficient);
        } else {
            ITEM_COEFFICIENT.remove(item);
        }
    }

    /**
     * 将<b>单把工具</b>与<b>一组</b>护甲材料绑定（一对多）。
     *
     * <p>工具侧按登记顺序取第一个能反推出有效基数的材料作为共享 B；
     * 重复调用以最后一次登记为准（整组替换）。
     *
     * @param item      具体的工具物品
     * @param materials 该工具对应的护甲材料数组（空数组或全部为 {@code null} 取消绑定）
     */
    public static void registerToolArmorMaterials(Item item, ArmorMaterial... materials) {
        List<ArmorMaterial> list = new ArrayList<>();
        if (materials != null) {
            for (ArmorMaterial material : materials) {
                if (material != null) {
                    list.add(material);
                }
            }
        }
        if (item == null || list.isEmpty()) {
            ITEM_ARMOR_MATERIAL.remove(item);
        } else {
            ITEM_ARMOR_MATERIAL.put(item, list);
        }
    }

    /**
     * 将<b>单把工具</b>与<b>一组</b>护甲材料绑定（一对多），并同时为其登记修正系数。
     *
     * <p>语义等价于依次调用 {@link #registerToolArmorMaterials(Item, ArmorMaterial[])}
     * 与 {@link #registerToolCoefficient(Item, double)}；系数仅在绑定有效（基数可反推）时生效。
     *
     * @param item        具体的工具物品（{@code null} 视为取消登记）
     * @param coefficient 修正系数（> 0 登记；≤ 0 取消，恢复默认 1.0）
     * @param materials   该工具对应的护甲材料数组（空数组或全部为 {@code null} 取消绑定）
     */
    public static void registerToolArmorMaterials(Item item, double coefficient, ArmorMaterial... materials) {
        registerToolArmorMaterials(item, materials);
        if (item != null && coefficient > 0) {
            ITEM_COEFFICIENT.put(item, coefficient);
        } else {
            ITEM_COEFFICIENT.remove(item);
        }
    }

    /**
     * 为<b>单把工具</b>显式登记耐久基数（无护甲可依、或要覆写护甲反推时用）。
     *
     * <p>Item 级优先级：显式登记 &gt; 护甲反推（显式数值是作者最强控制；
     * 常规场景优先用 {@link #registerToolArmorMaterial} 绑定护甲材料以与护甲共享 B）。
     *
     * @param item 具体的工具物品
     * @param base 耐久基数（> 0 注册；≤ 0 取消注册）
     */
    public static void registerToolDurabilityBase(Item item, double base) {
        if (item != null && base > 0) {
            ITEM_BASE.put(item, base);
        } else {
            ITEM_BASE.remove(item);
        }
    }

    /**
     * 为<b>单把工具</b>显式登记耐久基数，并同时登记修正系数（"显式基数 + 系数"一步到位）。
     *
     * <p>语义等价于依次调用 {@link #registerToolDurabilityBase(Item, double)}
     * 与 {@link #registerToolCoefficient(Item, double)}；系数仅在基数有效时生效。
     *
     * @param item        具体的工具物品
     * @param base        耐久基数（> 0 注册；≤ 0 取消注册）
     * @param coefficient 修正系数（> 0 登记；≤ 0 取消，恢复默认 1.0）
     */
    public static void registerToolDurabilityBase(Item item, double base, double coefficient) {
        registerToolDurabilityBase(item, base);
        if (item != null && coefficient > 0) {
            ITEM_COEFFICIENT.put(item, coefficient);
        } else {
            ITEM_COEFFICIENT.remove(item);
        }
    }

    /**
     * 为<b>单把工具</b>显式登记修正系数（不登记时默认 1.0）。
     *
     * @param item        具体的工具物品
     * @param coefficient 修正系数（> 0 注册；≤ 0 取消注册，恢复默认）
     */
    public static void registerToolCoefficient(Item item, double coefficient) {
        if (item != null && coefficient > 0) {
            ITEM_COEFFICIENT.put(item, coefficient);
        } else {
            ITEM_COEFFICIENT.remove(item);
        }
    }

    /**
     * 查询 Item 是否显式登记了耐久基数。
     *
     * @param item 具体的工具物品
     * @return 登记的基数；未登记返回 {@code null}
     */
    public static Double durabilityBaseOf(Item item) {
        return ITEM_BASE.get(item);
    }

    /**
     * 查询 Item 是否显式登记了修正系数。
     *
     * @param item 具体的工具物品
     * @return 登记的系数；未登记返回 {@code null}
     */
    public static Double coefficientOf(Item item) {
        return ITEM_COEFFICIENT.get(item);
    }

    /**
     * 查询 Item 绑定的护甲材料集合（不可变视图；未绑定返回空列表）。
     *
     * @param item 具体的工具物品
     * @return 绑定的护甲材料（按登记顺序）；未绑定返回空列表
     */
    public static List<ArmorMaterial> armorMaterialsOf(Item item) {
        List<ArmorMaterial> list = ITEM_ARMOR_MATERIAL.get(item);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /**
     * 查询 Item 绑定的第一个护甲材料（单绑定场景的便捷入口）。
     *
     * @param item 具体的工具物品
     * @return 第一个绑定的护甲材料；未绑定返回 {@code null}
     */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包按 Item 查询，IDE 无调用方属预期
    public static ArmorMaterial armorMaterialOf(Item item) {
        List<ArmorMaterial> list = ITEM_ARMOR_MATERIAL.get(item);
        return list == null || list.isEmpty() ? null : list.get(0);
    }
}
