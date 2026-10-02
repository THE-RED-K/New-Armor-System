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
 * Everything is Copper（万物皆铜，modId: {@code everythingcopper}）联动。
 * <p>
 * 职责：把该模组的铜护甲材料、护甲物品、工具物品导入新护甲系统
 * （通过 {@link CompatRegistration#registerMaterialRulesForTools} 等 API），
 * 使其获得护甲类型、耐久、质量、修理、工具耐久等机制；
 * 并通过 {@link CompatArmorAttributes} 覆写铜护甲<b>护甲值 / 盔甲韧性</b>
 * （数值见下方常量：已按梯度重排，并非该模组原版数值；受实验性总开关门控）。
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位常量（0 / null = 不登记该维度，
 * NAS 自动走默认值或反推，行为安全），由使用者自行填写。
 * 注意两套语义：材料规则 0 = 不登记；护甲值/韧性 0 = 该部位移除（NAS 语义，见
 * {@link ArmorAttributeRules}），四部位全填 0 才等效"不登记"。
 * <p>
 * 通过 ForgeRegistries 按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>
 * everythingcopper jar；未安装该模组时 {@link #registerAll()} 直接返回，不产生任何影响。
 * <p>
 * 参考（Everything is Copper dev-1.20.0 分支，与 1.20.1 注册一致）：
 * <ul>
 *   <li>铜护甲材料 {@code ICopperItem.COPPER_MATERIAL}：原版耐久 = 槽位表
 *       {盔10/胸15/腿14/靴12} × 12 = 120/180/168/144，防御 2/5/4/2，附魔 12；</li>
 *   <li>铜工具 {@code ICopperItem.COPPER_TIER}（ForgeTier 2）：原版耐久 180；
 *       <ul>
 *         <li>copper_sword / copper_pickaxe / copper_axe / copper_shovel / copper_hoe</li>
 *       </ul></li>
 *   <li>铜剪刀 copper_shears：独立耐久体系（原版 200），不继承 COPPER_TIER，可选登记；</li>
 *   <li>氧化变种（exposed / weathered / oxidized）为 NBT 状态，非独立物品，无额外登记。</li>
 * </ul>
 */
public final class EverythingIsCopperCompat {

    public static final String MOD_ID = "everythingcopper";

    private EverythingIsCopperCompat() {
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
    // 当前已填数值：铜耐久基数 12、质量系数 1.15、MEDIUM（用户指定）。
    // 效果预览（B=12, MEDIUM, 工具系数 1）：
    //   护甲成品 = 13/16/15/12 × 12 = 156/192/180/144（原版 120/180/168/144，
    //             即 +30%/+7%/+7%/+0%，落在 NAS 原版提升基准内）；
    //   工具     = 12^2 × 1 = 144（原版 180，-20%，与 NAS 木工具 -17% 同级）。
    // ============================================================

    // ---- 铜 COPPER（全套护甲 + 全套工具 + 剪刀可选）----
    private static final ArmorClass COPPER_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double COPPER_DURABILITY_BASE = 12;
    private static final double COPPER_MASS_COEFFICIENT = 1.15;
    private static final double COPPER_REPAIR_COEFFICIENT = -1;
    private static final double COPPER_TOOL_COEFFICIENT = 1;

    // ==================== 护甲值 / 盔甲韧性（NAS 1.0.1 ArmorAttributeRules API） ====================
    // 以下数值是本模组的参考值（已按梯度重排，并非 Everything is Copper 原版数值）；受实验性总开关 experimentalFeaturesEnabled 门控，默认关闭时不生效。
    // 注意：与上方"0 = 不登记"不同——此处 0 = 该部位移除护甲值/韧性（NAS 语义），
    // 四个部位全填 0 才等效"不登记"。支持小数。
    // 原版数值：铜 {盔2, 胸5, 腿4, 靴2}，韧性 0（参考 ICopperItem.COPPER_MATERIAL；
    // 原版韧性若为非 0，请改为原版值）。
    // ================================================================================

    /** 铜护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] COPPER_ARMOR = {2, 5, 4, 2};
    /** 铜盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double COPPER_TOUGHNESS = 1.5;

    // ---- 铜剪刀（独立耐久体系，不继承 COPPER_TIER；可选登记；≤0 = 不登记）----
    private static final double COPPER_SHEARS_DURABILITY_BASE = -1;
    private static final double COPPER_SHEARS_TOOL_COEFFICIENT = 1;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装 Everything is Copper 时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerCopper();
        registerShears();
    }

    // ============================================================
    // 逐套注册
    // ============================================================

    private static void registerCopper() {
        ArmorMaterial material = armorMaterialOf(
                "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                COPPER_ARMOR_CLASS, COPPER_DURABILITY_BASE, COPPER_MASS_COEFFICIENT,
                COPPER_REPAIR_COEFFICIENT, COPPER_TOOL_COEFFICIENT,
                item("copper_sword"), item("copper_pickaxe"), item("copper_axe"),
                item("copper_shovel"), item("copper_hoe"));
        registerArmorAttributes(material, COPPER_ARMOR, COPPER_TOUGHNESS);
    }

    /** 铜剪刀：无对应护甲材料，仅支持显式耐久基数（按需填写上方占位常量）。 */
    private static void registerShears() {
        if (COPPER_SHEARS_DURABILITY_BASE <= 0 && COPPER_SHEARS_TOOL_COEFFICIENT <= 0) {
            return;
        }
        Item shears = item("copper_shears");
        if (shears == null) {
            return;
        }
        ItemDurabilityRules.registerToolDurabilityBase(shears, COPPER_SHEARS_DURABILITY_BASE);
        ItemDurabilityRules.registerToolCoefficient(shears, COPPER_SHEARS_TOOL_COEFFICIENT);
    }

    // ============================================================
    // 辅助：按注册 ID 运行时获取物品 / 护甲材料（无编译期依赖）
    // ============================================================

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
    }

    /** 按材料 × 部位登记四个部位的护甲值与统一韧性（NAS 1.0.1 ArmorAttributeRules API）。 */
    private static void registerArmorAttributes(ArmorMaterial material, double[] armor, double toughness) {
        ArmorItem.Type[] types = {
                ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
        };
        for (int i = 0; i < 4; i++) {
            CompatArmorAttributes.register(material, types[i], armor[i], toughness);
        }
    }

    private static ArmorMaterial armorMaterialOf(String... piecePaths) {
        for (String path : piecePaths) {
            Item i = item(path);
            if (i instanceof ArmorItem armor) {
                return armor.getMaterial();
            }
        }
        return null;
    }
}
