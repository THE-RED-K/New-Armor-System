package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

import javax.annotation.Nullable;
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
 * @author THEREDK
 * @reason 护甲类型分类统一
 */
public enum ArmorClass {
    LIGHT,
    MEDIUM,
    HEAVY;

    /** 自定义护甲材料 → 分类登记表（内置材料走 switch，不经过此表）。 */
    private static final Map<ArmorMaterial, ArmorClass> CUSTOM_CLASSES = new IdentityHashMap<>();

    /**
     * 查询护甲材料所属分类。
     *
     * <p>登记表优先于内置规则：内置材料与枚举扩展材料（其他模组用 EnumHelper
     * 注入的 {@code ArmorMaterials} 值）均可通过 {@link #register} 覆盖分类；
     * 未登记的枚举扩展材料与自定义材料默认 {@link #MEDIUM}。
     *
     * @param material 护甲材料
     * @return 分类；永不返回 {@code null}
     */
    public static ArmorClass of(ArmorMaterial material) {
        ArmorClass registered = CUSTOM_CLASSES.get(material);  // 登记表优先（含枚举扩展覆盖）
        if (registered != null) {
            return registered;
        }
        if (material instanceof ArmorMaterials am) {
            return switch (am) {
                case LEATHER, CHAIN -> LIGHT;    // 轻甲：皮革、锁链（锁链因此耐久低于铁套）
                case GOLD, IRON, DIAMOND, NETHERITE -> MEDIUM;
                case TURTLE -> HEAVY;            // 重甲：海龟
                default -> MEDIUM;               // 枚举扩展材料默认中甲，可 register 覆盖
            };
        }
        return MEDIUM;                           // 未登记的自定义材料默认中甲
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
