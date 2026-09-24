package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 护甲类型分类（轻甲 / 中甲 / 重甲）—— 全模组统一区分入口。
 *
 * <p>所有需要区分护甲类型的功能（耐久基数、铁砧修理、减伤等）一律通过
 * {@link #of(ArmorMaterial)} 查询分类，禁止各自维护重复的 switch 分类逻辑。
 *
 * <p>内置分类规则：轻甲 LIGHT = 皮革、锁链；中甲 MEDIUM = 金、铁、钻石、下界合金；
 * 重甲 HEAVY = 海龟。
 *
 * <p>自定义护甲材料可调用 {@link #register(ArmorMaterial, ArmorClass)} 登记分类；
 * 未登记的自定义材料默认按中甲 MEDIUM 处理（系统策略，可通过 register 覆盖）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：{@code ArmorMaterials} 由枚举变为<b>注册表 + Holder</b>，
 * {@code ArmorMaterial} 成为 record，故内置材料不再能 {@code instanceof} 枚举判断；
 * 改为按注册表条目实例（{@code Holder#value()}，条目唯一实例）比对。
 * 1.21 新增的 {@code ARMADILLO}（狼铠材料）不设内置分类与基数：
 * 分类回退默认中甲、耐久基数由物品原版耐久反推，行为与原版保持一致。
 *
 * @author THEREDK
 * @reason 护甲类型分类统一
 */
public enum ArmorClass {
    LIGHT,
    MEDIUM,
    HEAVY;

    /** 自定义护甲材料 → 分类登记表（内置材料走内置规则，不经过此表）。 */
    private static final Map<ArmorMaterial, ArmorClass> CUSTOM_CLASSES = new IdentityHashMap<>();

    /**
     * 查询护甲材料所属分类。
     *
     * <p>登记表优先于内置规则；未登记的材料（含 1.21 新增的 {@code ARMADILLO}
     * 与第三方自定义材料）默认 {@link #MEDIUM}。
     *
     * @param material 护甲材料（注册表条目实例）
     * @return 分类；永不返回 {@code null}
     */
    public static ArmorClass of(ArmorMaterial material) {
        ArmorClass registered = CUSTOM_CLASSES.get(material);  // 登记表优先
        if (registered != null) {
            return registered;
        }
        if (material == ArmorMaterials.LEATHER.value() || material == ArmorMaterials.CHAIN.value()) {
            return LIGHT;                        // 轻甲：皮革、锁链（锁链因此耐久低于铁套）
        }
        if (material == ArmorMaterials.TURTLE.value()) {
            return HEAVY;                        // 重甲：海龟
        }
        return MEDIUM;                           // 金/铁/钻石/下界合金及未登记材料默认中甲
    }

    /** 为自定义护甲材料登记分类。 */
    public static void register(ArmorMaterial material, ArmorClass clazz) {
        CUSTOM_CLASSES.put(material, clazz);
    }

    /** 护甲类型系数（AT）：最终耐久 = 部位系数 × 材料耐久基数 × AT。 */
    public double ArmorTypeCoefficient() {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return switch (this) {
            case LIGHT -> loaded ? Config.COMMON.lightArmorCoefficient.get() : 0.75;
            case MEDIUM -> loaded ? Config.COMMON.mediumArmorCoefficient.get() : 1.0;
            case HEAVY -> loaded ? Config.COMMON.heavyArmorCoefficient.get() : 1.5;
        };
    }

    /** 修理效率系数：单个材料修复量 = 耐久基数 × 4 × AT / 修理效率系数。 */
    public double repairEfficiencyCoefficient() {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return switch (this) {
            case LIGHT -> loaded ? Config.COMMON.lightRepairEfficiencyCoefficient.get() : 1.5;
            case MEDIUM -> loaded ? Config.COMMON.mediumRepairEfficiencyCoefficient.get() : 1.0;
            case HEAVY -> loaded ? Config.COMMON.heavyRepairEfficiencyCoefficient.get() : 0.75;
        };
    }
}
