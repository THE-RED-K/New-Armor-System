package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.CompatRegistration;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraft.resources.ResourceLocation;

/**
 * 冰火传说（Ice and Fire: Dragons, modId {@code iceandfire}）联动。
 *
 * <p>结构要点（基于 2.1.13-1.20.1-beta-5 反编译）：
 * <ul>
 *   <li>护甲材料是 {@code IafItemRegistry} 的静态字段（citadel
 *       {@code CustomArmorMaterial} 子类 {@code IafArmorMaterial}），每个材料被 2-12 件
 *       护甲共享（银/铜/羊各 4 件、耳塞/眼罩各 1 件、蠕虫/蚁/龙钢各 3-2 色 × 4 件），
 *       <b>按材料登记 13 次</b>（用该组任一件护甲的 {@code getMaterial()} 获取实例）即可。</li>
 *   <li>材料槽位表<b>沿用原版</b> {@code {11,16,15,13}}（盔/胸/腿/靴）；源码按
 *       {@code makeDefense(靴, 腿, 胸, 盔)} 的顺序书写，故字面量看起来是
 *       {@code {13,15,16,11}} ——<b>既不是自定义表，顺序也不能照抄</b>。
 *       与 NAS 部位系数 {@code {13,16,15,12}} 相比：胸/腿完全相同（16/15），
 *       仅头盔 11→13、靴子 13→12 各差 2 与 1，耐久反推不失真。</li>
 *   <li>巨魔套/海蛇套/龙鳞套（12 色）在 2.1.13 中<b>未注册为玩家物品</b>
 *       （龙鳞套 ItemScaleArmor 是给龙穿戴的装甲），无物品可登记。</li>
 *   <li><b>工具结论</b>：
 *       <ul>
 *         <li>ItemModSword/Axe/Pickaxe/Hoe/Shovel 系列（银/铜/龙骨/龙钢/蚁/dread 全部工具）
 *             实现 {@code DragonSteelOverrides} 覆盖 {@code getMaxDamage(ItemStack)} ——
 *             NAS 工具公式注入在无参 {@code Item.getMaxDamage()} HEAD，虚分派不经过子类
 *             覆盖体，<b>登记无效</b>。</li>
 *         <li>NAS 公式<b>会生效</b>的 6 个独立剑类（SwordItem 且未覆盖）：
 *             ghost_sword（base 3000）、dragonbone_sword_fire/ice/lightning（base 2000，
 *             ItemAlchemySword）、hippogryph_sword / hippocampus_slapper /
 *             stymphalian_bird_dagger / amphithere_macuahuitl（base 500）。它们无对应护甲
 *             材料，如需纳入公式只能用 {@code registerToolDurabilityBase} 显式登记
 *             （默认留空 = 保持原版耐久）。</li>
 *         <li>弓/杖/权杖/三叉戟/长矛/法杖/拳套等非 {@code TieredItem}，NAS 天然跳过。</li>
 *       </ul></li>
 * </ul>
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位（null / ≤0 = 不登记该维度，
 * 保持原版 / 全局默认）。ARMOR_CLASS 给建议分类，可按世界观调整。
 *
 * <p>原版数值表（源码按 <b>靴→腿→胸→盔</b> 书写，下表已转写为游戏内显示顺序 <b>盔/胸/腿/靴</b>，
 * 勿照抄源码字面量；耐久 = maxDamageFactor × 原版部位系数 {11,16,15,13}）：
 * <pre>
 *   材料组               | 部件数 | 原版耐久(盔胸腿靴)      | 防御(盔胸腿靴) | 附魔 | 韧性
 *   SILVER 银            |   4    | 165/240/225/195       | 2/5/4/1      |  20  | 0
 *   COPPER 铜            |   4    | 110/160/150/130       | 2/4/3/1      |  15  | 0
 *   SHEEP 羊             |   4    | 55/80/75/65           | 1/2/3/1      |  15  | 0
 *   EARPLUGS 耳塞        |   1    | 65（仅头盔）           | 1            |  10  | 0
 *   BLINDFOLD 眼罩       |   1    | 65（仅头盔）           | 1            |  10  | 0
 *   DEATHWORM 蠕虫(黄白红)| 4/色  | 165/240/225/195       | 3/7/5/2      |   5  | 1.5
 *   MYRMEX 蚁(荒漠/丛林)  | 4/色  | 220/320/300/260       | 4/8/5/3      |  15  | 0
 *   DRAGONSTEEL 龙钢(火冰雷)| 4/色 | 1760/2560/2400/2080 | 7/12/9/6     |  30  | 6.0
 * </pre>
 * 龙钢耐久/防御由配置驱动：耐久 = 槽位系数 × 0.02 × dragonsteelBaseDurabilityEquipment（默认
 * 8000），防御 = 12 − {5,0,3,6}（<b>盔/胸/腿/靴</b> = 7/12/9/6；源码按靴→盔书写故字面为
 * 12 − {6,3,0,5}，其中胸甲减 0 即"满值 12"，可作顺序判据），韧性 6.0。
 *
 * @author nascompat
 */
public final class IceAndFireCompat {

    private static final String MOD_ID = "iceandfire";

    // ============================================================
    // 数值（占位：null / ≤0 = 不登记；改这里即可）
    // ============================================================

    // ---- 银套 SILVER（armor_silver_metal_helmet / _chestplate / _leggings / _boots） ----
    private static final ArmorClass SILVER_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double SILVER_DURABILITY_BASE = 11;
    private static final double SILVER_MASS_COEFFICIENT = 1.2;
    private static final double SILVER_REPAIR_COEFFICIENT = -1;

    // ---- 铜套 COPPER（armor_copper_metal_helmet / _chestplate / _leggings / _boots） ----
    private static final ArmorClass COPPER_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double COPPER_DURABILITY_BASE = 12;
    private static final double COPPER_MASS_COEFFICIENT = 1.15;
    private static final double COPPER_REPAIR_COEFFICIENT = -1;

    // ---- 羊套 SHEEP（sheep_helmet / _chestplate / _leggings / _boots） ----
    private static final ArmorClass SHEEP_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double SHEEP_DURABILITY_BASE = 5;
    private static final double SHEEP_MASS_COEFFICIENT = 0.25;
    private static final double SHEEP_REPAIR_COEFFICIENT = -1;

    // ---- 耳塞 EARPLUGS（earplugs，头盔位） ----
    private static final ArmorClass EARPLUGS_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double EARPLUGS_DURABILITY_BASE = 5;
    private static final double EARPLUGS_MASS_COEFFICIENT = 0.1;
    private static final double EARPLUGS_REPAIR_COEFFICIENT = -1;

    // ---- 眼罩 BLINDFOLD（blindfold，头盔位） ----
    private static final ArmorClass BLINDFOLD_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double BLINDFOLD_DURABILITY_BASE = 5;
    private static final double BLINDFOLD_MASS_COEFFICIENT = 0.1;
    private static final double BLINDFOLD_REPAIR_COEFFICIENT = -1;

    // ---- 死亡蠕虫 DEATHWORM（deathworm_yellow / _white / _red 各 4 件） ----
    private static final ArmorClass DEATHWORM_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double DEATHWORM_DURABILITY_BASE = 15;
    private static final double DEATHWORM_MASS_COEFFICIENT = 0.6;
    private static final double DEATHWORM_REPAIR_COEFFICIENT = -1;

    // ---- 蚁人 MYRMEX（myrmex_desert / _jungle 各 4 件） ----
    private static final ArmorClass MYRMEX_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double MYRMEX_DURABILITY_BASE = 19;
    private static final double MYRMEX_MASS_COEFFICIENT = 0.7;
    private static final double MYRMEX_REPAIR_COEFFICIENT = -1;

    // ---- 龙钢 DRAGONSTEEL（dragonsteel_fire / _ice / _lightning 各 4 件） ----
    private static final ArmorClass DRAGONSTEEL_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double DRAGONSTEEL_DURABILITY_BASE = 160;
    private static final double DRAGONSTEEL_MASS_COEFFICIENT = 1.25;
    private static final double DRAGONSTEEL_REPAIR_COEFFICIENT = -1;

    // ============================================================================
    // 护甲值 / 盔甲韧性（数值改写维度）—— 经 CompatArmorAttributes 门控：
    // feature_toggles.experimentalFeaturesEnabled，默认关闭时完全不生效。
    //
    // 数组顺序固定 = {头盔, 胸甲, 护腿, 靴子}；韧性四部位统一。支持小数。
    // 注意语义：与上方"≤ 0 = 不登记"不同，这里 0 = 该部位【移除】护甲值/韧性（NAS 语义），
    // 四部位全 0 才等效"不登记"。
    //
    // 下列数值取自类注释的"防御(盔胸腿靴)/韧性"参考列（= 冰火原版成品数值），
    // 作为 NAS 的改写基线；若要像暮色森林那样"按梯度重排"，直接改这里的数组即可。
    //
    // 注意 1：这些是冰火原值的【有序化副本】，<b>不是</b> NAS 重排 —— 按本模组的口径，
    //         "照抄原值"不算数值改写，启用实验性开关后建议按 NAS 梯度重写（参照 VanillaArmorCompat 的档位）。
    // 注意 2：数组已统一转写为游戏内显示顺序 {头盔, 胸甲, 护腿, 靴子}；冰火源码沿用了原版
    //         makeDefense(靴, 腿, 胸, 盔) 的书写顺序，直接照抄会得到"靴 > 头"的错误分配。
    // ============================================================================

    /** 银套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] SILVER_ARMOR = {2, 5, 3, 1};
    /** 银套盔甲韧性。 */
    private static final double SILVER_TOUGHNESS = 1.0;

    /** 铜套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] COPPER_ARMOR = {2, 5, 4, 2};
    /** 铜套盔甲韧性。 */
    private static final double COPPER_TOUGHNESS = 1.5;

    /** 羊套护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] SHEEP_ARMOR = {1, 3, 2, 1};
    /** 羊套盔甲韧性。 */
    private static final double SHEEP_TOUGHNESS = 0.0;

    /** 耳塞护甲值（单件、仅头盔位；其余部位填 0 = 不提供，该材料本来也无这些部位的物品）。 */
    private static final double[] EARPLUGS_ARMOR = {1, 0, 0, 0};
    /** 耳塞盔甲韧性。 */
    private static final double EARPLUGS_TOUGHNESS = 0.0;

    /** 眼罩护甲值（单件、仅头盔位；其余部位填 0 = 不提供）。 */
    private static final double[] BLINDFOLD_ARMOR = {1, 0, 0, 0};
    /** 眼罩盔甲韧性。 */
    private static final double BLINDFOLD_TOUGHNESS = 0.0;

    /** 死亡蠕虫套护甲值 {头盔, 胸甲, 护腿, 靴子}（黄/白/红三色共用同一组数值）。 */
    private static final double[] DEATHWORM_ARMOR = {3, 7, 6, 2};
    /** 死亡蠕虫盔甲韧性（三色共用）。 */
    private static final double DEATHWORM_TOUGHNESS = 1.5;

    /** 蚁人套护甲值 {头盔, 胸甲, 护腿, 靴子}（荒漠/丛林共用同一组数值）。 */
    private static final double[] MYRMEX_ARMOR = {4, 8, 7, 3};
    /** 蚁人盔甲韧性（两色共用）。 */
    private static final double MYRMEX_TOUGHNESS = 0.0;

    /** 龙钢套护甲值 {头盔, 胸甲, 护腿, 靴子}（火/冰/雷三系共用同一组数值）。 */
    private static final double[] DRAGONSTEEL_ARMOR = {10, 30, 25, 10};
    /** 龙钢盔甲韧性（三系共用）。 */
    private static final double DRAGONSTEEL_TOUGHNESS = 10.0;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装冰火传说时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerMaterial("armor_silver_metal_helmet", SILVER_ARMOR_CLASS,
                SILVER_DURABILITY_BASE, SILVER_MASS_COEFFICIENT, SILVER_REPAIR_COEFFICIENT,
                SILVER_ARMOR, SILVER_TOUGHNESS);
        registerMaterial("armor_copper_metal_helmet", COPPER_ARMOR_CLASS,
                COPPER_DURABILITY_BASE, COPPER_MASS_COEFFICIENT, COPPER_REPAIR_COEFFICIENT,
                COPPER_ARMOR, COPPER_TOUGHNESS);
        registerMaterial("sheep_helmet", SHEEP_ARMOR_CLASS,
                SHEEP_DURABILITY_BASE, SHEEP_MASS_COEFFICIENT, SHEEP_REPAIR_COEFFICIENT,
                SHEEP_ARMOR, SHEEP_TOUGHNESS);
        registerMaterial("earplugs", EARPLUGS_ARMOR_CLASS,
                EARPLUGS_DURABILITY_BASE, EARPLUGS_MASS_COEFFICIENT, EARPLUGS_REPAIR_COEFFICIENT,
                EARPLUGS_ARMOR, EARPLUGS_TOUGHNESS);
        registerMaterial("blindfold", BLINDFOLD_ARMOR_CLASS,
                BLINDFOLD_DURABILITY_BASE, BLINDFOLD_MASS_COEFFICIENT, BLINDFOLD_REPAIR_COEFFICIENT,
                BLINDFOLD_ARMOR, BLINDFOLD_TOUGHNESS);
        registerMaterial("deathworm_yellow_helmet", DEATHWORM_ARMOR_CLASS,
                DEATHWORM_DURABILITY_BASE, DEATHWORM_MASS_COEFFICIENT, DEATHWORM_REPAIR_COEFFICIENT,
                DEATHWORM_ARMOR, DEATHWORM_TOUGHNESS);
        registerMaterial("deathworm_white_helmet", DEATHWORM_ARMOR_CLASS,
                DEATHWORM_DURABILITY_BASE, DEATHWORM_MASS_COEFFICIENT, DEATHWORM_REPAIR_COEFFICIENT,
                DEATHWORM_ARMOR, DEATHWORM_TOUGHNESS);
        registerMaterial("deathworm_red_helmet", DEATHWORM_ARMOR_CLASS,
                DEATHWORM_DURABILITY_BASE, DEATHWORM_MASS_COEFFICIENT, DEATHWORM_REPAIR_COEFFICIENT,
                DEATHWORM_ARMOR, DEATHWORM_TOUGHNESS);
        registerMaterial("myrmex_desert_helmet", MYRMEX_ARMOR_CLASS,
                MYRMEX_DURABILITY_BASE, MYRMEX_MASS_COEFFICIENT, MYRMEX_REPAIR_COEFFICIENT,
                MYRMEX_ARMOR, MYRMEX_TOUGHNESS);
        registerMaterial("myrmex_jungle_helmet", MYRMEX_ARMOR_CLASS,
                MYRMEX_DURABILITY_BASE, MYRMEX_MASS_COEFFICIENT, MYRMEX_REPAIR_COEFFICIENT,
                MYRMEX_ARMOR, MYRMEX_TOUGHNESS);
        registerMaterial("dragonsteel_fire_helmet", DRAGONSTEEL_ARMOR_CLASS,
                DRAGONSTEEL_DURABILITY_BASE, DRAGONSTEEL_MASS_COEFFICIENT, DRAGONSTEEL_REPAIR_COEFFICIENT,
                DRAGONSTEEL_ARMOR, DRAGONSTEEL_TOUGHNESS);
        registerMaterial("dragonsteel_ice_helmet", DRAGONSTEEL_ARMOR_CLASS,
                DRAGONSTEEL_DURABILITY_BASE, DRAGONSTEEL_MASS_COEFFICIENT, DRAGONSTEEL_REPAIR_COEFFICIENT,
                DRAGONSTEEL_ARMOR, DRAGONSTEEL_TOUGHNESS);
        registerMaterial("dragonsteel_lightning_helmet", DRAGONSTEEL_ARMOR_CLASS,
                DRAGONSTEEL_DURABILITY_BASE, DRAGONSTEEL_MASS_COEFFICIENT, DRAGONSTEEL_REPAIR_COEFFICIENT,
                DRAGONSTEEL_ARMOR, DRAGONSTEEL_TOUGHNESS);
    }

    /**
     * 登记单套材料：机制适配（分类/耐久基数/质量/修理 —— 始终生效）
     * + 数值改写（护甲值/韧性 —— 经 {@link CompatArmorAttributes} 门控）。
     *
     * @param representativePiece 该材料的代表性部件（用于运行时反查材料实例）
     * @param clazz               护甲类型
     * @param base                耐久基数（≤ 0 = 不登记，按物品原版耐久反推）
     * @param mass                材料质量系数（≤ 0 = 不登记，默认 1.0）
     * @param repair              铁砧修理花费系数（≤ 0 = 不登记，用全局配置）
     * @param armor               {头盔, 胸甲, 护腿, 靴子} 四部位护甲值
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
     * 关闭时不写任何值，各护甲保持冰火自己的数值。
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
