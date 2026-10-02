package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorAttributeRules;
import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.CompatRegistration;
import dev.newarmorsystem.api.ItemDurabilityRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Pure Emerald Tools（纯净绿宝石工具，modId: {@code pureemeraldtools}）联动。
 * <p>
 * 职责：把该模组三套（绿宝石 / 纯绿宝石 / 绿宝石下界合金）的护甲材料、护甲物品、
 * 工具物品导入新护甲系统，使其获得护甲类型、耐久、质量、修理、工具耐久等机制；
 * 并通过 {@link CompatArmorAttributes} 覆写护甲<b>护甲值 / 盔甲韧性</b>
 * （数值见下方常量：已按梯度重排，并非该模组原版数值；受实验性总开关门控）。
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位常量（0 / null = 不登记该维度，
 * NAS 自动走默认值或反推，行为安全），由使用者自行填写。
 * 注意两套语义：材料规则 0 = 不登记；护甲值/韧性 0 = 该部位移除（NAS 语义，见
 * {@link ArmorAttributeRules}），四部位全填 0 才等效"不登记"。
 * <p>
 * 通过 ForgeRegistries 按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>
 * pureemeraldtools jar；未安装该模组时 {@link #registerAll()} 直接返回，不产生任何影响。
 * <p>
 * <b>本模组特有注意点</b>：每件护甲物品各自 {@code new ArmorMaterial(){...}}（匿名类），
 * 同一套的 4 件材料是<b>不同对象实例</b>；NAS 登记表按对象实例匹配
 * （{@code IdentityHashMap}），因此<b>必须逐件护甲登记</b>材料规则，不能只取一件代表。
 * <p>
 * 参考（Pure Emerald Tools 1.20 分支，1.20.1 Forge v2.0.0）：
 * <ul>
 *   <li>三套护甲均用自定义槽位表 {@code {13,15,16,11}}（head/chest/legs/feet）：
 *       <ul>
 *         <li>绿宝石 emerald：×23 → 原版成品 299/345/368/253，防御 3/5/6/3，附魔 10；</li>
 *         <li>纯绿宝石 pure_emerald：×35 → 455/525/560/385，防御 3/6/8/3，附魔 12；</li>
 *         <li>绿宝石下界合金 emerald_netherite：×40 → 520/600/640/440，防御 3/6/8/3，附魔 18。</li>
 *       </ul>
 *       <b>警告</b>：该槽位表与原版 {@code {11,16,15,13}} 不同，未显式登记耐久基数时
 *       NAS 按原版表反推会失真（每件结果不同），建议尽量填写 durabilityBase。</li>
 *   <li>三套工具（注册名 {@code prefix + _sword/_pickaxe/_axe/_shovel/_hoe}）：
 *       绿宝石 uses=684（铁级）、纯绿宝石 uses=1656（钻级）、
 *       绿宝石下界合金 uses 更高（修复物为下界合金碎片）；</li>
 *   <li>纯绿宝石原矿物品 {@code pure_emerald} / {@code pure_emerald_block} 非装备，无需登记。</li>
 * </ul>
 */
public final class PureEmeraldToolsCompat {

    public static final String MOD_ID = "pureemeraldtools";

    private PureEmeraldToolsCompat() {
    }

    // ============================================================
    // 数值占位（请自行填写；0 / null = 不登记该维度）
    //
    //   armorClass            护甲类型：ArmorClass.LIGHT / MEDIUM / HEAVY；
    //                         不登记时自定义材料默认 MEDIUM
    //   durabilityBase        耐久基数 B（>0 登记，应满足 B = 4 × 莫氏硬度；≤0 不登记，NAS 按原版数值反推）
    //   materialMassCoefficient  材料质量系数（≥0 登记，0 = 零质量；<0 不登记，默认 1.0）
    //   repairCostCoefficient    铁砧修理花费系数（≥0 登记，0 = 免费；<0 不登记，用全局配置）
    //   toolCoefficient          工具修正系数（>0 登记；≤0 不登记，默认 1.0）
    //
    // 当前已填数值：绿宝石套 耐久基数 30、质量系数 0.35、MEDIUM（用户指定）；
    // 纯绿宝石套与绿宝石下界合金套数值未指定，暂留占位（不登记，走 NAS 默认/反推）。
    // 效果预览（绿宝石 B=30, MEDIUM, 工具系数 1）：
    //   护甲成品 = 13/16/15/12 × 30 = 390/480/450/360（原版 299/345/368/253，
    //             即 +30%/+39%/+22%/+42%，落在 NAS 原版提升基准内）；
    //   工具     = 30^2 × 1 = 900（原版 684，+32%，介于 NAS 铁 256 与钻 1600 之间）。
    // ============================================================

    // ---- 绿宝石 EMERALD（已填）----
    private static final ArmorClass EMERALD_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double EMERALD_DURABILITY_BASE = 30;
    private static final double EMERALD_MASS_COEFFICIENT = 0.35;
    private static final double EMERALD_REPAIR_COEFFICIENT = -1;
    private static final double EMERALD_TOOL_COEFFICIENT = 1;

    // ==================== 护甲值 / 盔甲韧性（NAS 1.0.1 ArmorAttributeRules API） ====================
    // 以下数值是本模组的参考值（已按梯度重排，并非 Pure Emerald Tools 原版数值）；受实验性总开关 experimentalFeaturesEnabled 门控，默认关闭时不生效。
    // 注意：与上方"0 = 不登记"不同——此处 0 = 该部位移除护甲值/韧性（NAS 语义），
    // 四个部位全填 0 才等效"不登记"。支持小数。
    // 原版数值：绿宝石 {盔3, 胸5, 腿6, 靴3}，韧性 0（参考注释头部槽位表；
    // 原版韧性若为非 0，请改为原版值）。其余两套未登记，需要时仿照增加。
    // ================================================================================

    /** 绿宝石护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] EMERALD_ARMOR = {3, 7.5, 6.5, 3};
    /** 绿宝石盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double EMERALD_TOUGHNESS = 1.0;

    // ---- 纯绿宝石 PURE_EMERALD（占位，数值未指定）----
    private static final ArmorClass PURE_EMERALD_ARMOR_CLASS = null;
    private static final double PURE_EMERALD_DURABILITY_BASE = -1;
    private static final double PURE_EMERALD_MASS_COEFFICIENT = -1;
    private static final double PURE_EMERALD_REPAIR_COEFFICIENT = -1;
    private static final double PURE_EMERALD_TOOL_COEFFICIENT = 1;

    // ---- 绿宝石下界合金 EMERALD_NETHERITE（占位，数值未指定）----
    private static final ArmorClass EMERALD_NETHERITE_ARMOR_CLASS = null;
    private static final double EMERALD_NETHERITE_DURABILITY_BASE = -1;
    private static final double EMERALD_NETHERITE_MASS_COEFFICIENT = -1;
    private static final double EMERALD_NETHERITE_REPAIR_COEFFICIENT = -1;
    private static final double EMERALD_NETHERITE_TOOL_COEFFICIENT = 1;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装 Pure Emerald Tools 时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerSet("emerald",
                EMERALD_ARMOR_CLASS, EMERALD_DURABILITY_BASE, EMERALD_MASS_COEFFICIENT,
                EMERALD_REPAIR_COEFFICIENT, EMERALD_TOOL_COEFFICIENT);
        registerArmorSet("emerald", EMERALD_ARMOR, EMERALD_TOUGHNESS);
        registerSet("pure_emerald",
                PURE_EMERALD_ARMOR_CLASS, PURE_EMERALD_DURABILITY_BASE, PURE_EMERALD_MASS_COEFFICIENT,
                PURE_EMERALD_REPAIR_COEFFICIENT, PURE_EMERALD_TOOL_COEFFICIENT);
        registerSet("emerald_netherite",
                EMERALD_NETHERITE_ARMOR_CLASS, EMERALD_NETHERITE_DURABILITY_BASE, EMERALD_NETHERITE_MASS_COEFFICIENT,
                EMERALD_NETHERITE_REPAIR_COEFFICIENT, EMERALD_NETHERITE_TOOL_COEFFICIENT);
    }

    // ============================================================
    // 逐套注册（Pure Emerald Tools 每件护甲是独立匿名 ArmorMaterial 实例，
    // NAS 按实例匹配 → 4 件必须逐件登记材料规则）
    // ============================================================

    private static void registerSet(String prefix, ArmorClass clazz, double base,
                                    double mass, double repair, double toolCoefficient) {
        ArmorMaterial[] materials = armorMaterialsOf(
                prefix + "_armor_helmet", prefix + "_armor_chestplate",
                prefix + "_armor_leggings", prefix + "_armor_boots");
        if (materials.length == 0) {
            return;
        }
        // 逐件登记材料侧规则（护甲类型 / 耐久基数 / 质量系数 / 修理系数）
        for (ArmorMaterial material : materials) {
            CompatRegistration.registerMaterialRulesForTools(
                    material, clazz, base, mass, repair, toolCoefficient);
        }
        // 工具绑定：与本套第一件护甲的材料实例共享 B（任一件均可，4 件登记的 B 相同）
        for (String tool : new String[]{"_sword", "_pickaxe", "_axe", "_shovel", "_hoe"}) {
            Item toolItem = item(prefix + tool);
            if (toolItem != null) {
                ItemDurabilityRules.registerToolArmorMaterial(toolItem, materials[0], toolCoefficient);
            }
        }
    }

    /**
     * 逐件登记一套护甲的护甲值/韧性（NAS 1.0.1 ArmorAttributeRules API）。
     * <p>本模组每件护甲是独立匿名 {@link ArmorMaterial} 实例，NAS 按实例匹配，
     * 因此必须按部位逐一从物品取出材料实例登记，不能只取一件代表。
     *
     * @param armor     {头盔, 胸甲, 护腿, 靴子}，支持小数；null = 不登记本套
     * @param toughness 统一韧性，0 = 该部位移除韧性（NAS 语义）
     */
    private static void registerArmorSet(String prefix, double[] armor, double toughness) {
        if (armor == null) {
            return;
        }
        String[] piecePaths = {"_armor_helmet", "_armor_chestplate", "_armor_leggings", "_armor_boots"};
        ArmorItem.Type[] types = {
                ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
        };
        for (int i = 0; i < 4; i++) {
            Item piece = item(prefix + piecePaths[i]);
            if (piece instanceof ArmorItem armorItem) {
                CompatArmorAttributes.register(armorItem.getMaterial(), types[i], armor[i], toughness);
            }
        }
    }

    // ============================================================
    // 辅助：按注册 ID 运行时获取物品 / 护甲材料（无编译期依赖）
    // ============================================================

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
    }

    /** 收集一套护甲的全部材料实例（每件独立匿名实例，全部需要登记）。 */
    private static ArmorMaterial[] armorMaterialsOf(String... piecePaths) {
        ArmorMaterial[] result = new ArmorMaterial[piecePaths.length];
        int count = 0;
        for (String path : piecePaths) {
            Item i = item(path);
            if (i instanceof ArmorItem armor) {
                result[count++] = armor.getMaterial();
            }
        }
        if (count == piecePaths.length) {
            return result;
        }
        ArmorMaterial[] trimmed = new ArmorMaterial[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }
}
