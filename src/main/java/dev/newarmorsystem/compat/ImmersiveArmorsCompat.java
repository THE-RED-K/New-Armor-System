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
 * 沉浸式盔甲（Immersive Armors，modId: {@code immersive_armors}）联动。
 * <p>
 * 职责：把该模组 10 套护甲材料导入新护甲系统（护甲类型、耐久、质量、修理），
 * 使其获得护甲类型、耐久、质量、修理等机制。
 * <p>
 * <b>版本注意</b>：1.7.2 版本存在严重 bug，请使用 <b>1.7.1</b> 版本联动。
 * （注册 ID 以 1.20.1 分支 / 1.7.1 为准。）
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位常量（null / ≤0 = 不登记该维度，
 * NAS 自动走默认值或反推，行为安全），由使用者自行填写。
 * <p>
 * 通过 ForgeRegistries 按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>
 * immersive_armors jar；未安装该模组时 {@link #registerAll()} 直接返回，不产生任何影响。
 * <p>
 * 参考（Immersive Armors 1.20.1 分支，1.7.x）：
 * <ul>
 *   <li><b>10 套 × 4 件 = 40 件护甲，无工具/武器</b>。注册 ID 统一
 *       {@code {set}_helmet / {set}_chestplate / {set}_leggings / {set}_boots}。</li>
 *   <li>每套一个静态 {@code ExtendedArmorMaterial} 实例（{@code BONE_ARMOR} 等），
 *       4 件共享同一实例，<b>任取一件护甲 getMaterial() 即可登记整套</b>。</li>
 *   <li>材料槽位表与原版一致（{@code {13,15,16,11}} 靴/腿/胸/盔），
 *       未登记耐久基数时 NAS 按原版表反推不会失真。</li>
 *   <li>各套装原版数值（durabilityMultiplier × 槽位表 = 原版成品耐久；防御 盔/胸/腿/靴）：
 *       <ul>
 *         <li>bone 骨质       ：×8  → 88/128/120/104，防御 1/3/2/1，附魔 15</li>
 *         <li>wither 凋灵     ：×12 → 132/192/180/156，防御 2/4/3/2，附魔 0</li>
 *         <li>warrior 战士    ：×15 → 165/240/225/195，防御 2/5/6/2，韧性 1.0，附魔 5</li>
 *         <li>heavy 重型      ：×20 → 220/320/300/260，防御 4/6/5/3，韧性 4.0，击退抗性 0.5，附魔 6</li>
 *         <li>robe 长袍       ：×14 → 154/224/210/182，防御 2/3/2/1，附魔 50（可染色）</li>
 *         <li>slime 史莱姆    ：×20 → 220/320/300/260，防御 3/5/4/2，击退抗性 0.25，附魔 10</li>
 *         <li>divine 神圣     ：×18 → 198/288/270/234，防御 3/7/5/3，附魔 30（可染色）</li>
 *         <li>prismarine 海晶 ：×18 → 198/288/270/234，防御 3/8/6/3，附魔 8</li>
 *         <li>wooden 木质     ：×8  → 88/128/120/104，防御 1/3/2/1，附魔 4</li>
 *         <li>steampunk 蒸汽朋克：×10 → 110/160/150/130，防御 3/6/3/2，附魔 4</li>
 *       </ul></li>
 * </ul>
 */
public final class ImmersiveArmorsCompat {

    public static final String MOD_ID = "immersive_armors";

    private ImmersiveArmorsCompat() {
    }

    // ============================================================
    // 数值占位（请自行填写；null / ≤0 = 不登记该维度）
    //
    //   armorClass            护甲类型：ArmorClass.LIGHT / MEDIUM / HEAVY；
    //                         不登记时自定义材料默认 MEDIUM
    //   durabilityBase        耐久基数 B（>0 登记，应满足 B = 4 × 莫氏硬度；≤0 不登记，NAS 按原版数值反推）
    //   materialMassCoefficient  材料质量系数（≥0 登记，0 = 零质量；<0 不登记，默认 1.0）
    //   repairCostCoefficient    铁砧修理花费系数（≥0 登记，0 = 免费；<0 不登记，用全局配置）
    //
    // 本模组无工具，故无工具系数项。
    // 注：该模组材料自带扩展属性（weight / knockbackReduction / extraHealth 等），
    // 若 NAS 后续支持对应体系，可另行联动；此处仅登记 NAS 现有的护甲侧维度。
    // ============================================================

    // ---- 骨质 BONE ----
    private static final ArmorClass BONE_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double BONE_DURABILITY_BASE = 10;
    private static final double BONE_MASS_COEFFICIENT = 0.25;
    private static final double BONE_REPAIR_COEFFICIENT = -1;

    // ---- 凋灵 WITHER ----
    private static final ArmorClass WITHER_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double WITHER_DURABILITY_BASE = 15;
    private static final double WITHER_MASS_COEFFICIENT = 0.35;
    private static final double WITHER_REPAIR_COEFFICIENT = -1;

    // ---- 战士 WARRIOR ----
    private static final ArmorClass WARRIOR_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double WARRIOR_DURABILITY_BASE = 16;
    private static final double WARRIOR_MASS_COEFFICIENT = 0.8;
    private static final double WARRIOR_REPAIR_COEFFICIENT = -1;

    // ---- 重型 HEAVY ----
    private static final ArmorClass HEAVY_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double HEAVY_DURABILITY_BASE = 16;
    private static final double HEAVY_MASS_COEFFICIENT = 1.0;
    private static final double HEAVY_REPAIR_COEFFICIENT = -1;

    // ---- 长袍 ROBE ----
    private static final ArmorClass ROBE_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double ROBE_DURABILITY_BASE = 12;
    private static final double ROBE_MASS_COEFFICIENT = 0.45;
    private static final double ROBE_REPAIR_COEFFICIENT = -1;

    // ---- 史莱姆 SLIME ----
    private static final ArmorClass SLIME_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double SLIME_DURABILITY_BASE = 18;
    private static final double SLIME_MASS_COEFFICIENT = 0.4;
    private static final double SLIME_REPAIR_COEFFICIENT = -1;

    // ---- 神圣 DIVINE ----
    private static final ArmorClass DIVINE_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double DIVINE_DURABILITY_BASE = 28;
    private static final double DIVINE_MASS_COEFFICIENT = 1.7;
    private static final double DIVINE_REPAIR_COEFFICIENT = -1;

    // ---- 海晶 PRISMARINE ----
    private static final ArmorClass PRISMARINE_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double PRISMARINE_DURABILITY_BASE = 20;
    private static final double PRISMARINE_MASS_COEFFICIENT = 0.5;
    private static final double PRISMARINE_REPAIR_COEFFICIENT = -1;

    // ---- 木质 WOODEN ----
    private static final ArmorClass WOODEN_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double WOODEN_DURABILITY_BASE = 14;
    private static final double WOODEN_MASS_COEFFICIENT = 0.45;
    private static final double WOODEN_REPAIR_COEFFICIENT = -1;

    // ---- 蒸汽朋克 STEAMPUNK ----
    private static final ArmorClass STEAMPUNK_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double STEAMPUNK_DURABILITY_BASE = 13;
    private static final double STEAMPUNK_MASS_COEFFICIENT = 1.35;
    private static final double STEAMPUNK_REPAIR_COEFFICIENT = -1;

    // ============================================================================
    // 护甲值 / 盔甲韧性（数值改写维度）—— 经 CompatArmorAttributes 门控：
    // feature_toggles.experimentalFeaturesEnabled，默认关闭时完全不生效。
    //
    // 数组顺序固定 = {头盔, 胸甲, 护腿, 靴子}；韧性四部位统一（未单列韧性的套 = 0）。支持小数。
    // 注意语义：与上方"≤ 0 = 不登记"不同，这里 0 = 该部位【移除】护甲值/韧性（NAS 语义）。
    //
    // 数值取自类注释的"防御 盔/胸/腿/靴"参考列（= 各套装原版数值），作为 NAS 的改写基线；
    // 若要像暮色森林那样"按梯度重排"，直接改这里的数组即可。
    // （套装自带的击退抗性不在此登记 —— 那是另一条属性，由质量系统统一供给。）
    // ============================================================================

    /** 骨质套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] BONE_ARMOR = {1, 3, 2, 1};
    /** 骨质套盔甲韧性。 */
    private static final double BONE_TOUGHNESS = 1.0;

    /** 凋灵套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] WITHER_ARMOR = {3, 9, 6, 3};
    /** 凋灵套盔甲韧性。 */
    private static final double WITHER_TOUGHNESS = 0.5;

    /** 战士套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] WARRIOR_ARMOR = {2, 7, 6, 2};
    /** 战士套盔甲韧性。 */
    private static final double WARRIOR_TOUGHNESS = 1.5;

    /** 重型套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] HEAVY_ARMOR = {3, 9, 7.5, 3};
    /** 重型套盔甲韧性。 */
    private static final double HEAVY_TOUGHNESS = 3.0;

    /** 长袍套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] ROBE_ARMOR = {1, 3, 2, 1};
    /** 长袍套盔甲韧性。 */
    private static final double ROBE_TOUGHNESS = 0.0;

    /** 史莱姆套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] SLIME_ARMOR = {1.5, 4.5, 3.5, 1.5};
    /** 史莱姆套盔甲韧性。 */
    private static final double SLIME_TOUGHNESS = 1.5;

    /** 神圣套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] DIVINE_ARMOR = {1.5, 11, 4, 1.5};
    /** 神圣套盔甲韧性。 */
    private static final double DIVINE_TOUGHNESS = 1.0;

    /** 海晶套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] PRISMARINE_ARMOR = {3, 10.5, 9, 3};
    /** 海晶套盔甲韧性。 */
    private static final double PRISMARINE_TOUGHNESS = -2.0;

    /** 木质套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] WOODEN_ARMOR = {2, 4, 3, 2};
    /** 木质套盔甲韧性。 */
    private static final double WOODEN_TOUGHNESS = 1.0;

    /** 蒸汽朋克套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] STEAMPUNK_ARMOR = {2.5, 7, 6, 2.5};
    /** 蒸汽朋克套盔甲韧性。 */
    private static final double STEAMPUNK_TOUGHNESS = 0.5;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装 Immersive Armors 时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerSet("bone", BONE_ARMOR_CLASS, BONE_DURABILITY_BASE, BONE_MASS_COEFFICIENT, BONE_REPAIR_COEFFICIENT,
                BONE_ARMOR, BONE_TOUGHNESS);
        registerSet("wither", WITHER_ARMOR_CLASS, WITHER_DURABILITY_BASE, WITHER_MASS_COEFFICIENT, WITHER_REPAIR_COEFFICIENT,
                WITHER_ARMOR, WITHER_TOUGHNESS);
        registerSet("warrior", WARRIOR_ARMOR_CLASS, WARRIOR_DURABILITY_BASE, WARRIOR_MASS_COEFFICIENT, WARRIOR_REPAIR_COEFFICIENT,
                WARRIOR_ARMOR, WARRIOR_TOUGHNESS);
        registerSet("heavy", HEAVY_ARMOR_CLASS, HEAVY_DURABILITY_BASE, HEAVY_MASS_COEFFICIENT, HEAVY_REPAIR_COEFFICIENT,
                HEAVY_ARMOR, HEAVY_TOUGHNESS);
        registerSet("robe", ROBE_ARMOR_CLASS, ROBE_DURABILITY_BASE, ROBE_MASS_COEFFICIENT, ROBE_REPAIR_COEFFICIENT,
                ROBE_ARMOR, ROBE_TOUGHNESS);
        registerSet("slime", SLIME_ARMOR_CLASS, SLIME_DURABILITY_BASE, SLIME_MASS_COEFFICIENT, SLIME_REPAIR_COEFFICIENT,
                SLIME_ARMOR, SLIME_TOUGHNESS);
        registerSet("divine", DIVINE_ARMOR_CLASS, DIVINE_DURABILITY_BASE, DIVINE_MASS_COEFFICIENT, DIVINE_REPAIR_COEFFICIENT,
                DIVINE_ARMOR, DIVINE_TOUGHNESS);
        registerSet("prismarine", PRISMARINE_ARMOR_CLASS, PRISMARINE_DURABILITY_BASE, PRISMARINE_MASS_COEFFICIENT, PRISMARINE_REPAIR_COEFFICIENT,
                PRISMARINE_ARMOR, PRISMARINE_TOUGHNESS);
        registerSet("wooden", WOODEN_ARMOR_CLASS, WOODEN_DURABILITY_BASE, WOODEN_MASS_COEFFICIENT, WOODEN_REPAIR_COEFFICIENT,
                WOODEN_ARMOR, WOODEN_TOUGHNESS);
        registerSet("steampunk", STEAMPUNK_ARMOR_CLASS, STEAMPUNK_DURABILITY_BASE, STEAMPUNK_MASS_COEFFICIENT, STEAMPUNK_REPAIR_COEFFICIENT,
                STEAMPUNK_ARMOR, STEAMPUNK_TOUGHNESS);
    }

    // ============================================================
    // 逐套注册（每套一个静态 ExtendedArmorMaterial 实例，4 件共享，
    // 任取一件 getMaterial() 即可登记整套；本模组无工具，工具位传空）
    // ============================================================

    /**
     * 登记单套材料：机制适配（分类/耐久基数/质量/修理 —— 始终生效）
     * + 数值改写（护甲值/韧性 —— 经 {@link CompatArmorAttributes} 门控）。
     *
     * @param set       套装前缀（用于拼出四件物品的注册名）
     * @param clazz     护甲类型
     * @param base      耐久基数（≤ 0 = 不登记，按物品原版耐久反推）
     * @param mass      材料质量系数（≤ 0 = 不登记，默认 1.0）
     * @param repair    铁砧修理花费系数（≤ 0 = 不登记，用全局配置）
     * @param armor     {头盔, 胸甲, 护腿, 靴子} 四部位护甲值
     * @param toughness 四部位统一的盔甲韧性
     */
    private static void registerSet(String set, ArmorClass clazz, double base,
                                    double mass, double repair, double[] armor, double toughness) {
        ArmorMaterial material = armorMaterialOf(set + "_helmet", set + "_chestplate",
                set + "_leggings", set + "_boots");
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
     * 关闭时不写任何值，各护甲保持沉浸式盔甲自己的数值。
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
