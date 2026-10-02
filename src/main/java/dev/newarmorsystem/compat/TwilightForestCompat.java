package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorAttributeRules;
import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.CompatRegistration;
import dev.newarmorsystem.api.ItemDurabilityRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * 暮色森林（The Twilight Forest，modId: {@code twilightforest}）联动。
 * <p>
 * 职责：把暮色森林的护甲材料、护甲物品、工具物品导入新护甲系统
 * （通过 {@link CompatRegistration#registerMaterialRulesForTools} 等 API），
 * 使其获得护甲类型、耐久、质量、修理、工具耐久等机制；
 * 并通过 {@link CompatArmorAttributes} 覆写各材料<b>护甲值 / 盔甲韧性</b>
 * （数值见下方常量：已按梯度重排，并非暮色森林原版数值；受实验性总开关门控）。
 * <p>
 * 数值参数均为占位常量，由使用者自行填写。注意两套语义：
 * <ul>
 *   <li>材料规则（护甲类型/耐久/质量/修理/工具）：0 / null = 不登记该维度，NAS 走默认；</li>
 *   <li>护甲值 / 韧性：以下数值为参考值（已重排，并非原版），填 0 = 该部位移除护甲值/韧性（NAS 语义，
 *       见 {@link ArmorAttributeRules}），全部部位填 0 才等效"不登记"。</li>
 * </ul>
 * <p>
 * 通过注册表按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>暮色森林 jar；
 * 未安装暮色森林时 {@link #registerAll()} 直接返回，不产生任何影响。
 *
 * <p><b>1.20.1 → 1.21.1</b>：物品/材料获取改走 {@link BuiltInRegistries#ITEM}
 * （NeoForge 已移除 {@code ForgeRegistries}）；1.21 的护甲材料是注册表条目，
 * 故 {@code ArmorItem#getMaterial()} 需 {@code .value()} 取实例。
 *
 * @author THEREDK
 */
public final class TwilightForestCompat {

    public static final String MOD_ID = "twilightforest";

    private TwilightForestCompat() {
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
    // ============================================================

    // ---- 娜迦 NAGA（仅胸甲 + 护腿，无工具）----
    private static final ArmorClass NAGA_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double NAGA_DURABILITY_BASE = 33;
    private static final double NAGA_MASS_COEFFICIENT = 0.65;
    private static final double NAGA_REPAIR_COEFFICIENT = -1;
    private static final double NAGA_TOOL_COEFFICIENT = 1;

    // ---- 铁木 IRONWOOD（全套护甲 + 全套工具）----
    private static final ArmorClass IRONWOOD_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double IRONWOOD_DURABILITY_BASE = 32;
    private static final double IRONWOOD_MASS_COEFFICIENT = 0.75;
    private static final double IRONWOOD_REPAIR_COEFFICIENT = -1;
    private static final double IRONWOOD_TOOL_COEFFICIENT = 1;

    // ---- 炽焰 FIERY（全套护甲 + 剑/镐）----
    private static final ArmorClass FIERY_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double FIERY_DURABILITY_BASE = 36;
    private static final double FIERY_MASS_COEFFICIENT = 0.6;
    private static final double FIERY_REPAIR_COEFFICIENT = -1;
    private static final double FIERY_TOOL_COEFFICIENT = 1;

    // ---- 钢叶 STEELEAF（全套护甲 + 全套工具）----
    private static final ArmorClass STEELEAF_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double STEELEAF_DURABILITY_BASE = 14;
    private static final double STEELEAF_MASS_COEFFICIENT = 0.95;
    private static final double STEELEAF_REPAIR_COEFFICIENT = -1;
    private static final double STEELEAF_TOOL_COEFFICIENT = 1;

    // ---- 骑士金属 KNIGHTMETAL（全套护甲 + 剑/镐/斧）----
    private static final ArmorClass KNIGHTMETAL_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double KNIGHTMETAL_DURABILITY_BASE = 31;
    private static final double KNIGHTMETAL_MASS_COEFFICIENT = 0.9;
    private static final double KNIGHTMETAL_REPAIR_COEFFICIENT = -1;
    private static final double KNIGHTMETAL_TOOL_COEFFICIENT = 1;

    // ---- 幻影 PHANTOM（仅头盔 + 胸甲，无工具）----
    private static final ArmorClass PHANTOM_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double PHANTOM_DURABILITY_BASE = 46;
    private static final double PHANTOM_MASS_COEFFICIENT = 0.9;
    private static final double PHANTOM_REPAIR_COEFFICIENT = -1;
    private static final double PHANTOM_TOOL_COEFFICIENT = 1;

    // ---- 雪怪 YETI（全套护甲，无工具）----
    private static final ArmorClass YETI_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double YETI_DURABILITY_BASE = 32;
    private static final double YETI_MASS_COEFFICIENT = 0.6;
    private static final double YETI_REPAIR_COEFFICIENT = -1;
    private static final double YETI_TOOL_COEFFICIENT = 1;

    // ---- 极地 ARCTIC（全套护甲，无工具）----
    private static final ArmorClass ARCTIC_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double ARCTIC_DURABILITY_BASE = 12;
    private static final double ARCTIC_MASS_COEFFICIENT = 0.45;
    private static final double ARCTIC_REPAIR_COEFFICIENT = -1;
    private static final double ARCTIC_TOOL_COEFFICIENT = 1;

    // ==================== 护甲值 / 盔甲韧性（NAS 1.0.1 ArmorAttributeRules API） ====================
    // 以下数值是本模组的参考值（已按梯度重排，并非暮色森林原版数值）；受实验性总开关 experimentalFeaturesEnabled 门控，默认关闭时不生效。
    // 注意：与上方"0 = 不登记"不同——此处 0 = 该部位移除护甲值/韧性（NAS 语义），
    // 四个部位全填 0 才等效"不登记"。支持小数。
    //
    // 原版数值表（来源：暮色 1.20.1 源码 TwilightArmorMaterial；护甲 map 按 靴→盔 定义，
    // 下方已统一转写为游戏内显示顺序 {盔, 胸, 腿, 靴}，勿照抄源码字面量）：
    // 娜迦 NAGA     {2, 7, 6, 3}  韧性 0.5   仅胸甲 + 护腿有物品
    // 铁木 IRONWOOD {2, 7, 5, 2}  韧性 0     全套
    // 炽焰 FIERY    {4, 9, 7, 4}  韧性 1.5   全套
    // 钢叶 STEELEAF {3, 8, 6, 3}  韧性 0     全套
    // 骑士金属      {3, 8, 6, 3}  韧性 1.0   全套
    // 幻影 PHANTOM  {3, 8, 6, 3}  韧性 2.5   仅头盔 + 胸甲有物品
    // 雪怪 YETI     {4, 7, 6, 3}  韧性 3.0   全套
    // 极地 ARCTIC   {2, 7, 5, 2}  韧性 2.0   全套
    // ================================================================================

    /** 娜迦护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] NAGA_ARMOR = {3, 7, 6, 3};
    /** 娜迦盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double NAGA_TOUGHNESS = 2.5;

    /** 铁木护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] IRONWOOD_ARMOR = {2.5, 7, 6, 2.5};
    /** 铁木盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double IRONWOOD_TOUGHNESS = 2.0;

    /** 炽焰护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] FIERY_ARMOR = {5, 15, 12, 5};
    /** 炽焰盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double FIERY_TOUGHNESS = 3.0;

    /** 钢叶护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] STEELEAF_ARMOR = {3, 10, 8, 3};
    /** 钢叶盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double STEELEAF_TOUGHNESS = 1.5;

    /** 骑士金属护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] KNIGHTMETAL_ARMOR = {4, 11, 9, 4};
    /** 骑士金属盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double KNIGHTMETAL_TOUGHNESS = 2.5;

    /** 幻影护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] PHANTOM_ARMOR = {4, 11, 9, 4};
    /** 幻影盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double PHANTOM_TOUGHNESS = 6.0;

    /** 雪怪护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] YETI_ARMOR = {6, 17, 14, 6};
    /** 雪怪盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double YETI_TOUGHNESS = 6.0;

    /** 极地护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] ARCTIC_ARMOR = {3, 8, 7, 3};
    /** 极地盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double ARCTIC_TOUGHNESS = 3.0;

    // ---- 特殊工具（无对应护甲材料，仅可选显式基数；0 = 不登记）----
    private static final double ICE_SWORD_DURABILITY_BASE = -1;
    private static final double ICE_SWORD_TOOL_COEFFICIENT = 1;
    private static final double GLASS_SWORD_DURABILITY_BASE = 1;
    private static final double GLASS_SWORD_TOOL_COEFFICIENT = 1;
    private static final double GIANT_SWORD_DURABILITY_BASE = 216;
    private static final double GIANT_SWORD_TOOL_COEFFICIENT = 0.25;
    private static final double GIANT_PICKAXE_DURABILITY_BASE = 216;
    private static final double GIANT_PICKAXE_TOOL_COEFFICIENT = 0.25;
    private static final double DIAMOND_MINOTAUR_AXE_DURABILITY_BASE = 40;
    private static final double DIAMOND_MINOTAUR_AXE_TOOL_COEFFICIENT = 1;
    private static final double GOLD_MINOTAUR_AXE_DURABILITY_BASE = 10;
    private static final double GOLD_MINOTAUR_AXE_TOOL_COEFFICIENT = 1;
    private static final double MAZEBREAKER_PICKAXE_DURABILITY_BASE = 40;
    private static final double MAZEBREAKER_PICKAXE_TOOL_COEFFICIENT = 1;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装暮色森林时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerNaga();
        registerIronwood();
        registerFiery();
        registerSteeleaf();
        registerKnightmetal();
        registerPhantom();
        registerYeti();
        registerArctic();
        registerSpecialTools();
    }

    // ============================================================
    // 逐套注册
    // ============================================================

    private static void registerNaga() {
        ArmorMaterial material = armorMaterialOf("naga_chestplate", "naga_leggings");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                NAGA_ARMOR_CLASS, NAGA_DURABILITY_BASE, NAGA_MASS_COEFFICIENT,
                NAGA_REPAIR_COEFFICIENT, NAGA_TOOL_COEFFICIENT);
        registerArmorAttributes(material, NAGA_ARMOR, NAGA_TOUGHNESS);
    }

    private static void registerIronwood() {
        ArmorMaterial material = armorMaterialOf(
                "ironwood_helmet", "ironwood_chestplate", "ironwood_leggings", "ironwood_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                IRONWOOD_ARMOR_CLASS, IRONWOOD_DURABILITY_BASE, IRONWOOD_MASS_COEFFICIENT,
                IRONWOOD_REPAIR_COEFFICIENT, IRONWOOD_TOOL_COEFFICIENT,
                item("ironwood_sword"), item("ironwood_pickaxe"), item("ironwood_axe"),
                item("ironwood_shovel"), item("ironwood_hoe"));
        registerArmorAttributes(material, IRONWOOD_ARMOR, IRONWOOD_TOUGHNESS);
    }

    private static void registerFiery() {
        ArmorMaterial material = armorMaterialOf(
                "fiery_helmet", "fiery_chestplate", "fiery_leggings", "fiery_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                FIERY_ARMOR_CLASS, FIERY_DURABILITY_BASE, FIERY_MASS_COEFFICIENT,
                FIERY_REPAIR_COEFFICIENT, FIERY_TOOL_COEFFICIENT,
                item("fiery_sword"), item("fiery_pickaxe"));
        registerArmorAttributes(material, FIERY_ARMOR, FIERY_TOUGHNESS);
    }

    private static void registerSteeleaf() {
        ArmorMaterial material = armorMaterialOf(
                "steeleaf_helmet", "steeleaf_chestplate", "steeleaf_leggings", "steeleaf_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                STEELEAF_ARMOR_CLASS, STEELEAF_DURABILITY_BASE, STEELEAF_MASS_COEFFICIENT,
                STEELEAF_REPAIR_COEFFICIENT, STEELEAF_TOOL_COEFFICIENT,
                item("steeleaf_sword"), item("steeleaf_pickaxe"), item("steeleaf_axe"),
                item("steeleaf_shovel"), item("steeleaf_hoe"));
        registerArmorAttributes(material, STEELEAF_ARMOR, STEELEAF_TOUGHNESS);
    }

    private static void registerKnightmetal() {
        ArmorMaterial material = armorMaterialOf(
                "knightmetal_helmet", "knightmetal_chestplate", "knightmetal_leggings", "knightmetal_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                KNIGHTMETAL_ARMOR_CLASS, KNIGHTMETAL_DURABILITY_BASE, KNIGHTMETAL_MASS_COEFFICIENT,
                KNIGHTMETAL_REPAIR_COEFFICIENT, KNIGHTMETAL_TOOL_COEFFICIENT,
                item("knightmetal_sword"), item("knightmetal_pickaxe"), item("knightmetal_axe"));
        registerArmorAttributes(material, KNIGHTMETAL_ARMOR, KNIGHTMETAL_TOUGHNESS);
    }

    private static void registerPhantom() {
        ArmorMaterial material = armorMaterialOf("phantom_helmet", "phantom_chestplate");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                PHANTOM_ARMOR_CLASS, PHANTOM_DURABILITY_BASE, PHANTOM_MASS_COEFFICIENT,
                PHANTOM_REPAIR_COEFFICIENT, PHANTOM_TOOL_COEFFICIENT);
        registerArmorAttributes(material, PHANTOM_ARMOR, PHANTOM_TOUGHNESS);
    }

    private static void registerYeti() {
        ArmorMaterial material = armorMaterialOf(
                "yeti_helmet", "yeti_chestplate", "yeti_leggings", "yeti_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                YETI_ARMOR_CLASS, YETI_DURABILITY_BASE, YETI_MASS_COEFFICIENT,
                YETI_REPAIR_COEFFICIENT, YETI_TOOL_COEFFICIENT);
        registerArmorAttributes(material, YETI_ARMOR, YETI_TOUGHNESS);
    }

    private static void registerArctic() {
        ArmorMaterial material = armorMaterialOf(
                "arctic_helmet", "arctic_chestplate", "arctic_leggings", "arctic_boots");
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(material,
                ARCTIC_ARMOR_CLASS, ARCTIC_DURABILITY_BASE, ARCTIC_MASS_COEFFICIENT,
                ARCTIC_REPAIR_COEFFICIENT, ARCTIC_TOOL_COEFFICIENT);
        registerArmorAttributes(material, ARCTIC_ARMOR, ARCTIC_TOUGHNESS);
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

    /** 特殊工具：无对应护甲材料，仅支持显式耐久基数（按需填写上方占位常量）。 */
    private static void registerSpecialTools() {
        registerStandaloneTool(ICE_SWORD_DURABILITY_BASE, ICE_SWORD_TOOL_COEFFICIENT, "ice_sword");
        registerStandaloneTool(GLASS_SWORD_DURABILITY_BASE, GLASS_SWORD_TOOL_COEFFICIENT, "glass_sword");
        registerStandaloneTool(GIANT_SWORD_DURABILITY_BASE, GIANT_SWORD_TOOL_COEFFICIENT, "giant_sword");
        registerStandaloneTool(GIANT_PICKAXE_DURABILITY_BASE, GIANT_PICKAXE_TOOL_COEFFICIENT, "giant_pickaxe");
        registerStandaloneTool(DIAMOND_MINOTAUR_AXE_DURABILITY_BASE, DIAMOND_MINOTAUR_AXE_TOOL_COEFFICIENT,
                "diamond_minotaur_axe");
        registerStandaloneTool(GOLD_MINOTAUR_AXE_DURABILITY_BASE, GOLD_MINOTAUR_AXE_TOOL_COEFFICIENT,
                "gold_minotaur_axe");
        registerStandaloneTool(MAZEBREAKER_PICKAXE_DURABILITY_BASE, MAZEBREAKER_PICKAXE_TOOL_COEFFICIENT,
                "mazebreaker_pickaxe");
    }

    /** 为无护甲材料的单把工具登记显式耐久基数与修正系数（均 ≤ 0 时无操作）。 */
    private static void registerStandaloneTool(double durabilityBase, double toolCoefficient, String itemId) {
        if (durabilityBase <= 0 && toolCoefficient <= 0) {
            return;
        }
        Item tool = item(itemId);
        if (tool == null) {
            return;
        }
        ItemDurabilityRules.registerToolDurabilityBase(tool, durabilityBase);
        ItemDurabilityRules.registerToolCoefficient(tool, toolCoefficient);
    }

    // ============================================================
    // 辅助：按注册 ID 运行时获取物品 / 护甲材料（无编译期依赖）
    // ============================================================

    private static Item item(String path) {
        // ITEM 是默认值注册表：get() 未命中返回 Items.AIR（非 null），故须 containsKey 判存在，
        // 否则调用方的 null 守卫失效、规则会被静默登记到 air 上。
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    private static ArmorMaterial armorMaterialOf(String... piecePaths) {
        for (String path : piecePaths) {
            Item i = item(path);
            if (i instanceof ArmorItem armor) {
                // 1.21：材料是注册表条目，需取 value() 得到实例
                return armor.getMaterial().value();
            }
        }
        return null;
    }
}
