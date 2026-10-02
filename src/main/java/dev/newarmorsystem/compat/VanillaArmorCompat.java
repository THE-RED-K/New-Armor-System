package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorAttributeRules;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

/**
 * 原版护甲属性（护甲值 / 盔甲韧性）增强 —— NAS 1.0.1 新增 {@link ArmorAttributeRules} API 的应用。
 *
 * <p>背景：NAS 1.0.1 之前护甲值/韧性只能走原版公式（材料表固定值 + 固定韧性），
 * 各模组护甲数值都挤在原版区间（{2,5,6,2}、{3,6,8,3} 一类），定位重复、梯度拉不开。
 * 本类以原版 7 种材料为基准重排护甲值与韧性，为后续模组联动提供错开的数值标尺。
 *
 * <p>数值：下表是原版数值，下方常量则是重排后的增强值（并非原版）；它们只在实验性总开关 experimentalFeaturesEnabled 开启时生效，默认关闭时各护甲完全保持原版。
 * {@code armor} 数组顺序固定为 {@code {头盔, 胸甲, 护腿, 靴子}}；韧性四部位统一。
 * 护甲值与韧性均支持小数（如 1.5）。值必须 ≥ 0：0 = 该部位不再提供护甲值/韧性
 * （NAS 语义，见 {@link ArmorAttributeRules}）。
 *
 * <p>登记粒度：材料 × 部位，自动应用到该材料下全部护甲物品
 * （含其它模组复用原版材料的物品）；未登记的部位保持原版值。
 *
 * <p><b>1.20.1 → 1.21.1</b>：1.21 起原版护甲材料是注册表条目（{@code Holder<ArmorMaterial>}），
 * 故取实例须写 {@code ArmorMaterials.IRON.value()}；数值表本身两版相同（原版护甲值未变）。
 *
 * @author NAS Compatibility Patch Team
 */
public final class VanillaArmorCompat {

    private VanillaArmorCompat() {
    }

    // ==================== 原版数值表（盔/胸/腿/靴，1.21.1 与原版一致） ====================
    // 注意：原版 ArmorMaterials 源码按 靴→盔 顺序定义（BOOTS/LEGGINGS/CHESTPLATE/HELMET），
    // 下方数值已统一转写为游戏内显示顺序 {盔, 胸, 腿, 靴}，勿照抄原版数组字面量。
    // 皮革       {1, 3, 2, 1}  韧性 0
    // 锁链       {2, 5, 4, 1}  韧性 0
    // 铁         {2, 6, 5, 2}  韧性 0
    // 金         {2, 5, 3, 1}  韧性 0
    // 钻石       {3, 8, 6, 3}  韧性 2
    // 海龟       {2, 6, 5, 2}  韧性 0（仅头盔物品 turtle_helmet，其余部位无对应护甲）
    // 下界合金   {3, 8, 6, 3}  韧性 3
    // =====================================================================================

    /** 皮革护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] LEATHER_ARMOR = {1, 3, 2, 1};
    /** 皮革盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double LEATHER_TOUGHNESS = 0.0;

    /** 锁链护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] CHAIN_ARMOR = {1.5, 4.5, 3.5, 1.5};
    /** 锁链盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double CHAIN_TOUGHNESS = 0.0;

    /** 铁护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] IRON_ARMOR = {2, 6, 5, 2};
    /** 铁盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double IRON_TOUGHNESS = 2.0;

    /** 金护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] GOLD_ARMOR = {1, 4, 3, 1};
    /** 金盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double GOLD_TOUGHNESS = 0.5;

    /** 钻石护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] DIAMOND_ARMOR = {5, 13, 11, 5};
    /** 钻石盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double DIAMOND_TOUGHNESS = 1.0;

    /** 海龟护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] TURTLE_ARMOR = {3, 5, 4, 2};
    /** 海龟盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double TURTLE_TOUGHNESS = 3.0;

    /** 下界合金护甲值 {头盔, 胸甲, 护腿, 靴子}，参考值（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double[] NETHERITE_ARMOR = {6, 17, 14, 6};
    /** 下界合金盔甲韧性（已重排，并非原版），仅实验性总开关开启时生效。 */
    private static final double NETHERITE_TOUGHNESS = 5.0;

    /** 注册全部原版护甲增强。 */
    public static void registerAll() {
        register(ArmorMaterials.LEATHER.value(), LEATHER_ARMOR, LEATHER_TOUGHNESS);
        register(ArmorMaterials.CHAIN.value(), CHAIN_ARMOR, CHAIN_TOUGHNESS);
        register(ArmorMaterials.IRON.value(), IRON_ARMOR, IRON_TOUGHNESS);
        register(ArmorMaterials.GOLD.value(), GOLD_ARMOR, GOLD_TOUGHNESS);
        register(ArmorMaterials.DIAMOND.value(), DIAMOND_ARMOR, DIAMOND_TOUGHNESS);
        // 海龟壳仅头盔有物品，其余部位登记无副作用（无对应护甲物品）
        register(ArmorMaterials.TURTLE.value(), TURTLE_ARMOR, TURTLE_TOUGHNESS);
        register(ArmorMaterials.NETHERITE.value(), NETHERITE_ARMOR, NETHERITE_TOUGHNESS);
    }

    /**
     * 按材料 × 部位登记四个部位的护甲值与统一韧性。
     *
     * @param material  原版护甲材料实例（1.21 由 {@code Holder#value()} 取得）
     * @param armor     {头盔, 胸甲, 护腿, 靴子} 四部位护甲值（≥ 0）
     * @param toughness 四部位统一的盔甲韧性（≥ 0）
     */
    private static void register(ArmorMaterial material, double[] armor, double toughness) {
        ArmorItem.Type[] types = {
                ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
        };
        for (int i = 0; i < 4; i++) {
            CompatArmorAttributes.register(material, types[i], armor[i], toughness);
        }
    }
}
