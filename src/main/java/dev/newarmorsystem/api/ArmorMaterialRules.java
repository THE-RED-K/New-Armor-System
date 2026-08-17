package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 护甲材料耐久基数（对应现实莫氏硬度）—— 全模组统一查询入口。
 *
 * <p>内置材料规则与 {@link ArmorMaterials} 的 Mixin 耐久体系一致：
 * 皮革 8 / 金 10 / 铁 16 / 钻石 40 / 海龟 40（全部可配置）；
 * 锁链读铁套数值；下界合金失去自带基数，运行时按 钻石 + 金/2 联动。
 * 返回的是未乘部位系数与 ArmorTypeCoefficient 的原始基数。
 *
 * <p>自定义材料（其他模组实现 {@link ArmorMaterial} 接口）无内置基数表，
 * 通过 {@link #effectiveDurabilityBase} 按其自身 {@code getDurabilityForType}
 * 除以原版部位系数反推基数（假设其使用原版部位系数表）。
 *
 * <p>枚举扩展材料（其他模组用 EnumHelper 向 {@code ArmorMaterials} 注入的新值）
 * 属 {@code ArmorMaterials} 实例但无内置 switch 匹配，其耐久基数由
 * {@code ArmorMaterialsMixin} 在注入时反推并经 {@link #cacheExtendedDurabilityBase}
 * 缓存，{@link #effectiveDurabilityBase} 查缓存即可，避免再次触发注入造成递归。
 *
 * <p>设计定位：耐久基数统一查询，避免各处重复维护。
 *
 * @author THEREDK
 */
public final class ArmorMaterialRules {

    private ArmorMaterialRules() {
    }

    /** 枚举扩展材料（EnumHelper 注入的 {@code ArmorMaterials} 值）→ 反推基数缓存。 */
    private static final Map<ArmorMaterial, Double> EXTENDED_BASE_CACHE = new IdentityHashMap<>();

    /** 显式登记的耐久基数（兼容模组/整合包经 {@link #registerDurabilityBase} 登记，优先级最高）。 */
    private static final Map<ArmorMaterial, Double> REGISTERED_BASE = new IdentityHashMap<>();

    /**
     * 登记枚举扩展材料的反推耐久基数（由 {@code ArmorMaterialsMixin} 注入时调用）。
     *
     * <p>枚举扩展材料在内置 switch 无匹配，其 {@code getDurabilityForType} 首次执行时
     * 返回原版 部位系数×基数，Mixin 在 RETURN 处反推并登记；
     * 修复端 {@link #effectiveDurabilityBase} 直接查缓存，不再调用
     * {@code getDurabilityForType}（否则会二次触发注入，拿到的是已套新公式的值）。
     *
     * @param material 枚举扩展材料（必须是 {@code ArmorMaterials} 实例）
     * @param base     反推出的耐久基数
     */
    public static void cacheExtendedDurabilityBase(ArmorMaterial material, double base) {
        if (material instanceof ArmorMaterials && base > 0) {
            EXTENDED_BASE_CACHE.put(material, base);
        }
    }

    /**
     * 显式登记任意护甲材料的耐久基数（兼容模组 / 整合包作者入口）。
     *
     * <p>登记值 <b>优先于</b> 内置配置与反推结果，用于：修正使用非原版部位系数表的
     * 第三方材料（此时反推基数不准），或直接给纯自定义材料一个精确基数
     * （护甲与其绑定的工具会共享该值）。注册值 ≤ 0 视为取消登记。
     *
     * <p>登记表<b>非线程安全</b>，建议加载期调用（与 {@link DamageReflection#register} 同批）。
     *
     * @param material 护甲材料（内置或自定义均可；{@code null} 视为取消登记）
     * @param base     耐久基数（> 0 登记；≤ 0 取消登记）
     */
    public static void registerDurabilityBase(ArmorMaterial material, double base) {
        if (material != null && base > 0) {
            REGISTERED_BASE.put(material, base);
        } else {
            REGISTERED_BASE.remove(material);
        }
    }

    /**
     * 内置材料耐久基数。
     *
     * @param material 护甲材料（内置）
     * @return 基数；未知材料返回 -1（调用方应回退原版行为）
     */
    public static int durabilityBase(ArmorMaterial material) {
        if (!(material instanceof ArmorMaterials am)) {
            return -1; // 自定义材料：无内置基数
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return switch (am) {
            case LEATHER -> loaded ? Config.COMMON.leatherDurabilityBase.get() : 8;
            case GOLD -> loaded ? Config.COMMON.goldDurabilityBase.get() : 10;
            case CHAIN, IRON -> loaded ? Config.COMMON.ironDurabilityBase.get() : 16; // 锁链读铁
            case DIAMOND -> loaded ? Config.COMMON.diamondDurabilityBase.get() : 40;
            case NETHERITE -> (loaded ? Config.COMMON.diamondDurabilityBase.get() : 40)
                    + (loaded ? Config.COMMON.goldDurabilityBase.get() : 10) / 2;    // 钻石 + 金/2
            case TURTLE -> loaded ? Config.COMMON.turtleDurabilityBase.get() : 40;
            default -> -1;
        };
    }

    /**
     * 原版部位系数表（1.20.1）：HELMET=11, CHESTPLATE=16, LEGGINGS=15, BOOTS=13。
     *
     * <p>switch 已穷尽 {@link ArmorItem.Type}（普通枚举，仅四部位）全部常量，无需 default。
     *
     * @param type 护甲部位
     * @return 原版部位系数
     */
    public static int vanillaSlotFactor(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
    }

    /**
     * 可用于耐久 / 修复公式的耐久基数（内置、枚举扩展与自定义统一）。
     *
     * <p>优先级：显式登记（{@link #registerDurabilityBase}）&gt; 内置材料返回
     * {@link #durabilityBase}；枚举扩展材料（EnumHelper 注入的
     * {@code ArmorMaterials} 值）查 {@link #cacheExtendedDurabilityBase} 缓存；
     * 自定义材料（其他模组实现接口）按其自身 {@code getDurabilityForType(type)}
     * 除以 {@link #vanillaSlotFactor} 反推（Mixin 不作用于自定义类，无递归风险）。
     *
     * @param material 护甲材料
     * @param type     护甲部位（自定义材料反推需要）
     * @return 基数；无法确定返回 -1
     */
    public static double effectiveDurabilityBase(ArmorMaterial material, ArmorItem.Type type) {
        Double registered = REGISTERED_BASE.get(material);
        if (registered != null) {
            return registered; // 显式登记：覆写内置/枚举扩展/反推
        }
        if (material instanceof ArmorMaterials) {
            int base = durabilityBase(material);
            if (base >= 0) {
                return base;                       // 内置材料
            }
            Double cached = EXTENDED_BASE_CACHE.get(material);
            return cached != null ? cached : -1.0; // 枚举扩展材料：查反推缓存
        }
        return material.getDurabilityForType(type) / (double) vanillaSlotFactor(type);
    }
}
