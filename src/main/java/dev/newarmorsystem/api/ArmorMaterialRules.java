package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 护甲材料耐久基数与部位系数 —— 全模组统一查询入口。
 *
 * <p>内置材料规则：皮革 8 / 金 10 / 铁 16 / 钻石 40 / 海龟 40（全部可配置）；
 * 锁链读铁套数值；下界合金失去自带基数，运行时按 钻石 + 金/2 联动。
 * 返回的是未乘部位系数与 ArmorTypeCoefficient 的原始基数。
 *
 * <p>未知材料（1.21 新增的 {@code ARMADILLO} 及第三方自定义材料）无内置基数，
 * 由调用方传入物品的<b>原版耐久</b>，经 {@link #effectiveDurabilityBase}
 * 除以<b>原版部位系数</b>反推基数（假设其使用原版部位系数表）。
 *
 * <p><b>部位系数</b>：原版 {13,15,16,11,16}(boots/legs/chest/head/body) 与工作台配方无关联，
 * 这是原版的逻辑断裂。新系统下：部位系数 = 基底(默认8) + 制作所需材料个数，
 * 即 head=13, chest=16, legs=15, boots=12。原版部位系数可直接由
 * {@link ArmorItem.Type#getDurability(int)} 取（{@code getDurability(1)} 即系数本身）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：{@code ArmorMaterial} 已无 {@code getDurabilityForType}，
 * 护甲耐久改由物品的 {@code DataComponents.MAX_DAMAGE} 承载（见
 * {@link ArmorDurability}），故本类改为「材料 → 基数」的纯查询，
 * 不再有「枚举扩展材料反推缓存」（反推改由调用方按物品原版耐久完成）。
 *
 * <p>设计定位：耐久基数统一查询，避免各处重复维护。
 *
 * @author THEREDK
 */
public final class ArmorMaterialRules {

    private ArmorMaterialRules() {
    }

    /** 显式登记的耐久基数（兼容模组/整合包经 {@link #registerDurabilityBase} 登记，优先级最高）。 */
    private static final Map<ArmorMaterial, Double> REGISTERED_BASE = new IdentityHashMap<>();

    /** 装配期从物品原版耐久反推的基数缓存（未知材料；供运行期查询）。 */
    private static final Map<ArmorMaterial, Double> DERIVED_BASE_CACHE = new IdentityHashMap<>();

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
     * @param material 护甲材料（注册表条目实例）
     * @return 基数；未知材料返回 -1（调用方应从物品原版耐久反推）
     */
    public static int durabilityBase(ArmorMaterial material) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        if (material == ArmorMaterials.LEATHER.value()) {
            return loaded ? Config.COMMON.leatherDurabilityBase.get() : 8;
        }
        if (material == ArmorMaterials.GOLD.value()) {
            return loaded ? Config.COMMON.goldDurabilityBase.get() : 10;
        }
        if (material == ArmorMaterials.CHAIN.value() || material == ArmorMaterials.IRON.value()) {
            return loaded ? Config.COMMON.ironDurabilityBase.get() : 16;   // 锁链读铁
        }
        if (material == ArmorMaterials.DIAMOND.value()) {
            return loaded ? Config.COMMON.diamondDurabilityBase.get() : 40;
        }
        if (material == ArmorMaterials.NETHERITE.value()) {
            return (loaded ? Config.COMMON.diamondDurabilityBase.get() : 40)
                    + (loaded ? Config.COMMON.goldDurabilityBase.get() : 10) / 2;    // 钻石 + 金/2
        }
        if (material == ArmorMaterials.TURTLE.value()) {
            return loaded ? Config.COMMON.turtleDurabilityBase.get() : 40;
        }
        return -1;   // 未知材料（含 ARMADILLO 与自定义材料）：由调用方按物品原版耐久反推
    }

    /**
     * 原版部位系数表（1.21.1）：HELMET=11, CHESTPLATE=16, LEGGINGS=15, BOOTS=13, BODY=16。
     *
     * <p>1.21 起该表随 {@link ArmorItem.Type} 一同承载，可由
     * {@link ArmorItem.Type#getDurability(int)} 取（系数 × 1）。
     *
     * @param type 护甲部位
     * @return 原版部位系数
     */
    public static int vanillaSlotFactor(ArmorItem.Type type) {
        return type.getDurability(1);
    }

    /**
     * 本模组的新部位系数表：基底(默认 8) + 制作所需材料个数。
     *
     * @param type 护甲部位
     * @return 新部位系数（默认 head=13, chest=16, legs=15, boots=12）
     */
    public static int slotFactor(ArmorItem.Type type) {
        int base = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.slotFactorBase.get() : 8;
        return switch (type) {
            case HELMET -> base + 5;      // 5 个材料
            case CHESTPLATE -> base + 8;  // 8 个材料
            case LEGGINGS -> base + 7;    // 7 个材料
            case BOOTS -> base + 4;       // 4 个材料
            case BODY -> base + 8;        // 1.20.1 无此部位（1.21 狼铠）；按胸甲处理，与原版系数一致
        };
    }

    /**
     * 装配期解析耐久基数（见 {@link ArmorDurability}），未知材料的反推结果会写入缓存。
     *
     * <p>优先级：显式登记（{@link #registerDurabilityBase}）&gt; 内置材料
     * {@link #durabilityBase}；内置无匹配时（{@code ARMADILLO} 或第三方自定义材料）
     * 按传入的<b>物品原版耐久</b>除以 {@link #vanillaSlotFactor} 反推
     * （假设其使用原版部位系数表），并缓存供运行期查询。
     *
     * @param material         护甲材料
     * @param type             护甲部位（反推需要）
     * @param vanillaMaxDamage 该物品装配前的原版最大耐久（{@code DataComponents.MAX_DAMAGE}）；
     *                         未知或 ≤ 0 时无法反推
     * @return 基数；无法确定返回 -1
     */
    public static double resolveDurabilityBase(ArmorMaterial material, ArmorItem.Type type, int vanillaMaxDamage) {
        Double registered = REGISTERED_BASE.get(material);
        if (registered != null) {
            return registered;                  // 显式登记：覆写内置与反推
        }
        int base = durabilityBase(material);
        if (base >= 0) {
            return base;                        // 内置材料
        }
        int slotFactor = vanillaSlotFactor(type);
        if (vanillaMaxDamage > 0 && slotFactor > 0) {
            double derived = vanillaMaxDamage / (double) slotFactor;   // 未知材料：按物品原版耐久反推
            DERIVED_BASE_CACHE.put(material, derived);
            return derived;
        }
        return -1.0;
    }

    /**
     * 运行期查询耐久基数（耐久 / 铁砧修理 / 工具耐久公式统一入口）。
     *
     * <p>优先级：显式登记（{@link #registerDurabilityBase}）&gt; 内置材料
     * {@link #durabilityBase} &gt; 装配期反推缓存（由 {@link #resolveDurabilityBase} 写入）。
     *
     * @param material 护甲材料
     * @return 基数；无法确定返回 -1
     */
    public static double effectiveDurabilityBase(ArmorMaterial material) {
        Double registered = REGISTERED_BASE.get(material);
        if (registered != null) {
            return registered;
        }
        int base = durabilityBase(material);
        if (base >= 0) {
            return base;
        }
        Double cached = DERIVED_BASE_CACHE.get(material);
        return cached != null ? cached : -1.0;
    }
}
