package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.CompatRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Alex 的洞穴（Alex's Caves，modId: {@code alexscaves}）联动。
 * <p>
 * 职责：把该模组的护甲材料导入新护甲系统（护甲类型、耐久、质量、修理）。
 * <p>
 * <b>结构要点（基于 1.20.1 / 2.0.2 反编译）</b>：
 * <ul>
 *   <li>共 <b>6 组护甲材料</b>（每组材料被 2-4 件护甲<b>共享</b>，与 Alex's Mobs 的单件
 *       材料结构不同），合计 18 件护甲：primordial（3 件，无靴）、hazmat（4 件）、
 *       diving（4 件）、darkness（2 件，仅兜帽+披风）、rainbounce（1 件靴）、
 *       gingerbread（4 件）。</li>
 *   <li>材料为 {@code ACArmorMaterial} 实例（继承 {@code ArmorMaterial}），同一材料下
 *       所有部件共享规则，<b>按材料登记 6 次</b>（用该组任一件护甲的 getMaterial() 获取
 *       实例）即可，无需逐件登记。</li>
 *   <li>材料槽位表为自定义 {@code {13,15,16,11}}（盔/胸/腿/靴），NAS 部位系数为
 *       {@code {13,16,15,12}}：盔一致，胸/腿/靴各差 1（15vs16、16vs15、11vs12），
 *       未登记基数时反推基本不失真。</li>
 *   <li><b>工具无需登记</b>：全模组唯一 {@code TieredItem} 是 desolate_dagger
 *       （{@code SwordItem}，钻石级），但它覆盖了 {@code getMaxDamage()} 返回固定 360，
 *       NAS 工具公式注入在 {@code Item.getMaxDamage} 的 HEAD，虚分派不经过子类覆盖体，
 *       登记无效；其余武器（长矛/弓/射线枪/棍棒/拳套/盾/杖等）均非 {@code TieredItem}，
 *       NAS 工具公式天然跳过，保持原版耐久。</li>
 * </ul>
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位（null / ≤0 = 不登记该维度，
 * NAS 自动走默认值或反推，行为安全），由使用者自行填写。
 * <p>
 * 通过 ForgeRegistries 按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>
 * alexscaves jar；未安装该模组时 {@link #registerAll()} 直接返回，不产生任何影响。
 * <p>
 * 参考（Alex's Caves 2.0.2）——原版耐久 = multiplier × 槽位表{13,15,16,11}（盔/胸/腿/靴），
 * 防御显示顺序为 盔/胸/腿/靴：
 * <ul>
 *   <li><b>PRIMORDIAL 原始套</b>（multiplier 20）：
 *       primordial_helmet 盔 260（防3）、primordial_tunic 胸 300（防4）、
 *       primordial_pants 腿 320（防3）；附魔 25，韧性 0（无靴）</li>
 *   <li><b>HAZMAT 防护服</b>（multiplier 20）：
 *       hazmat_mask 盔 260（防2）、hazmat_chestplate 胸 300（防4）、
 *       hazmat_leggings 腿 320（防5）、hazmat_boots 靴 220（防2）；附魔 25，韧性 0.5</li>
 *   <li><b>DIVING 潜水服</b>（multiplier 20）：
 *       diving_helmet 盔 260（防2）、diving_chestplate 胸 300（防6）、
 *       diving_leggings 腿 320（防5）、diving_boots 靴 220（防2）；附魔 25，韧性 0</li>
 *   <li><b>DARKNESS 黑暗套</b>（multiplier 15）：
 *       hood_of_darkness 兜帽 195（防4）、cloak_of_darkness 披风 225（防5）；
 *       附魔 40，韧性 0.5（无腿/靴）</li>
 *   <li><b>RAINBOUNCE 彩虹弹跳</b>（multiplier 6）：
 *       rainbounce_boots 靴 66（防2）；附魔 40，韧性 0（仅 1 件）</li>
 *   <li><b>GINGERBREAD 姜饼套</b>（multiplier 10）：
 *       gingerbread_helmet 盔 130（防2）、gingerbread_chestplate 胸 150（防4）、
 *       gingerbread_leggings 腿 160（防5）、gingerbread_boots 靴 110（防2）；
 *       附魔 25，韧性 0</li>
 * </ul>
 */
public final class AlexsCavesCompat {

    /** 目标模组的 modId（本类所有注册 ID 都拼在它下面）。 */
    public static final String MOD_ID = "alexscaves";

    private AlexsCavesCompat() {
    }

    // ============================================================
    // 数值占位（请自行填写；null / ≤0 = 不登记该维度）
    //
    //   armorClass            护甲类型：ArmorClass.LIGHT / MEDIUM / HEAVY；
    //                         null 不登记（自定义材料默认 MEDIUM）
    //   durabilityBase        耐久基数 B（>0 登记，应满足 B = 4 × 莫氏硬度；≤0 不登记，NAS 按原版数值反推）
    //   materialMassCoefficient  材料质量系数（≥0 登记，0 = 零质量；<0 不登记，默认 1.0）
    //   repairCostCoefficient    铁砧修理花费系数（≥0 登记，0 = 免费；<0 不登记，用全局配置）
    //
    // 注：同一材料下所有部件共享规则（NAS 材料级登记，无部件级差异化）；
    //     材料自带特效（潜水供氧、防护服抗辐射、弹跳靴、姜饼回血等）
    //     不在 NAS 登记范围内。
    // ============================================================

    // ---- 原始套 PRIMORDIAL（primordial_helmet / _tunic / _pants） ----
    private static final ArmorClass PRIMORDIAL_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double PRIMORDIAL_DURABILITY_BASE = 24;
    private static final double PRIMORDIAL_MASS_COEFFICIENT = 0.5;
    private static final double PRIMORDIAL_REPAIR_COEFFICIENT = -1;

    // ---- 防护服 HAZMAT（hazmat_mask / _chestplate / _leggings / _boots） ----
    private static final ArmorClass HAZMAT_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double HAZMAT_DURABILITY_BASE = 20;
    private static final double HAZMAT_MASS_COEFFICIENT = 1.0;
    private static final double HAZMAT_REPAIR_COEFFICIENT = -1;

    // ---- 潜水服 DIVING（diving_helmet / _chestplate / _leggings / _boots） ----
    private static final ArmorClass DIVING_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double DIVING_DURABILITY_BASE = 24;
    private static final double DIVING_MASS_COEFFICIENT = 1.15;
    private static final double DIVING_REPAIR_COEFFICIENT = -1;

    // ---- 黑暗套 DARKNESS（hood_of_darkness / cloak_of_darkness） ----
    private static final ArmorClass DARKNESS_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double DARKNESS_DURABILITY_BASE = 15;
    private static final double DARKNESS_MASS_COEFFICIENT = 0.35;
    private static final double DARKNESS_REPAIR_COEFFICIENT = -1;

    // ---- 彩虹弹跳 RAINBOUNCE（rainbounce_boots） ----
    private static final ArmorClass RAINBOUNCE_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double RAINBOUNCE_DURABILITY_BASE = 8;
    private static final double RAINBOUNCE_MASS_COEFFICIENT = 0.0;
    private static final double RAINBOUNCE_REPAIR_COEFFICIENT = -1;

    // ---- 姜饼套 GINGERBREAD（gingerbread_helmet / _chestplate / _leggings / _boots） ----
    private static final ArmorClass GINGERBREAD_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double GINGERBREAD_DURABILITY_BASE = 10;
    private static final double GINGERBREAD_MASS_COEFFICIENT = 0.0;
    private static final double GINGERBREAD_REPAIR_COEFFICIENT = -1;

    // ============================================================================
    // 护甲值 / 盔甲韧性（数值改写维度）—— 经 CompatArmorAttributes 门控：
    // feature_toggles.experimentalFeaturesEnabled，默认关闭时完全不生效。
    //
    // 数组顺序固定 = {头盔, 胸甲, 护腿, 靴子}；韧性四部位统一（未单列韧性的组 = 0）。支持小数。
    // 注意语义：与上方"≤ 0 = 不登记"不同，这里 0 = 该部位【移除】护甲值/韧性（NAS 语义）；
    // 部分组缺部件（如原始套无靴、黑暗套只有兜帽+披风），缺失部位填 0 即可 ——
    // 该材料本来也没有对应物品，不会误伤别的东西。
    //
    // 数值取自类注释的"防 N"逐件参考值（= 各件原版数值），作为 NAS 的改写基线。
    // ============================================================================

    /** 原始套护甲值 {头盔, 胸甲, 护腿, 靴子}（无靴）。 */
    private static final double[] PRIMORDIAL_ARMOR = {3, 9, 7.5, 0};
    /** 原始套盔甲韧性。 */
    private static final double PRIMORDIAL_TOUGHNESS = 3.0;

    /** 防护服护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] HAZMAT_ARMOR = {1, 3, 2, 1};
    /** 防护服盔甲韧性。 */
    private static final double HAZMAT_TOUGHNESS = 0.5;

    /** 潜水服护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] DIVING_ARMOR = {1.5, 4.5, 4, 1.5};
    /** 潜水服盔甲韧性。 */
    private static final double DIVING_TOUGHNESS = 0.0;

    /** 黑暗套护甲值（兜帽 4 / 披风 5；无腿、无靴）。 */
    private static final double[] DARKNESS_ARMOR = {4, 8, 0, 0};
    /** 黑暗套盔甲韧性。 */
    private static final double DARKNESS_TOUGHNESS = 0.0;

    /** 彩虹弹跳护甲值（仅靴子，防 2）。 */
    private static final double[] RAINBOUNCE_ARMOR = {0, 0, 0, 2};
    /** 彩虹弹跳盔甲韧性。 */
    private static final double RAINBOUNCE_TOUGHNESS = 0.0;

    /** 姜饼套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] GINGERBREAD_ARMOR = {1, 3, 2, 1};
    /** 姜饼套盔甲韧性。 */
    private static final double GINGERBREAD_TOUGHNESS = 0.0;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装 Alex's Caves 时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerMaterial("primordial_helmet", PRIMORDIAL_ARMOR_CLASS,
                PRIMORDIAL_DURABILITY_BASE, PRIMORDIAL_MASS_COEFFICIENT, PRIMORDIAL_REPAIR_COEFFICIENT,
                PRIMORDIAL_ARMOR, PRIMORDIAL_TOUGHNESS);
        registerMaterial("hazmat_mask", HAZMAT_ARMOR_CLASS,
                HAZMAT_DURABILITY_BASE, HAZMAT_MASS_COEFFICIENT, HAZMAT_REPAIR_COEFFICIENT,
                HAZMAT_ARMOR, HAZMAT_TOUGHNESS);
        registerMaterial("diving_helmet", DIVING_ARMOR_CLASS,
                DIVING_DURABILITY_BASE, DIVING_MASS_COEFFICIENT, DIVING_REPAIR_COEFFICIENT,
                DIVING_ARMOR, DIVING_TOUGHNESS);
        registerMaterial("hood_of_darkness", DARKNESS_ARMOR_CLASS,
                DARKNESS_DURABILITY_BASE, DARKNESS_MASS_COEFFICIENT, DARKNESS_REPAIR_COEFFICIENT,
                DARKNESS_ARMOR, DARKNESS_TOUGHNESS);
        registerMaterial("rainbounce_boots", RAINBOUNCE_ARMOR_CLASS,
                RAINBOUNCE_DURABILITY_BASE, RAINBOUNCE_MASS_COEFFICIENT, RAINBOUNCE_REPAIR_COEFFICIENT,
                RAINBOUNCE_ARMOR, RAINBOUNCE_TOUGHNESS);
        registerMaterial("gingerbread_helmet", GINGERBREAD_ARMOR_CLASS,
                GINGERBREAD_DURABILITY_BASE, GINGERBREAD_MASS_COEFFICIENT, GINGERBREAD_REPAIR_COEFFICIENT,
                GINGERBREAD_ARMOR, GINGERBREAD_TOUGHNESS);
    }

    // ============================================================
    // 按材料注册（同材料所有部件共享规则）
    // ============================================================

    /**
     * 登记单组材料：机制适配（分类/耐久基数/质量/修理 —— 始终生效）
     * + 数值改写（护甲值/韧性 —— 经 {@link CompatArmorAttributes} 门控）。
     *
     * @param representativePiece 该材料的代表性部件（用于运行时反查材料实例）
     * @param clazz               护甲类型
     * @param base                耐久基数（≤ 0 = 不登记，按物品原版耐久反推）
     * @param mass                材料质量系数（≤ 0 = 不登记，默认 1.0）
     * @param repair              铁砧修理花费系数（≤ 0 = 不登记，用全局配置）
     * @param armor               {头盔, 胸甲, 护腿, 靴子} 四部位护甲值（缺部件填 0）
     * @param toughness           四部位统一的盔甲韧性
     */
    private static void registerMaterial(String representativePiece, ArmorClass clazz, double base,
                                         double mass, double repair, double[] armor, double toughness) {
        ArmorMaterial material = armorMaterialOf(representativePiece);
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(
                material, clazz, base, mass, repair, -1);
        registerArmorAttributes(material, armor, toughness);
    }

    /**
     * 按材料 × 部位登记四个部位的护甲值与统一韧性（数值改写维度）。
     *
     * <p>经 {@link CompatArmorAttributes} 转发，因此受
     * {@code feature_toggles.experimentalFeaturesEnabled}（默认关闭）门控 ——
     * 关闭时不写任何值，各护甲保持 Alex's Caves 自己的数值。
     *
     * @param material  护甲材料实例
     * @param armor     {头盔, 胸甲, 护腿, 靴子} 四部位护甲值（0 = 该部位不提供）
     * @param toughness 四部位统一的盔甲韧性（0 = 不提供）
     */
    private static void registerArmorAttributes(ArmorMaterial material, double[] armor, double toughness) {
        ArmorItem.Type[] types = {
                ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
        };
        for (int i = 0; i < 4; i++) {
            CompatArmorAttributes.register(material, types[i], armor[i], toughness);
        }
    }

    // ============================================================
    // 辅助：按注册 ID 运行时获取物品 / 护甲材料（无编译期依赖）
    // ============================================================

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
    }

    private static ArmorMaterial armorMaterialOf(String piecePath) {
        Item i = item(piecePath);
        if (i instanceof ArmorItem armor) {
            return armor.getMaterial();
        }
        return null;
    }
}
