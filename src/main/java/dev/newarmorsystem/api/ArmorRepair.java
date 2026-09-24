package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 护甲铁砧修理规则。
 *
 * <p>单个材料修复量 = 材料耐久基数 × 修理量乘数（默认 4，可配置
 * {@code repairMaterialMultiplier}）× 分类ArmorTypeCoefficient / 修理效率系数，
 * 且不超过当前损坏值（由 {@code AnvilMenu} 的 {@code Math.min} 天然约束）。
 *
 * <p>内置与自定义材料均纳入公式：内置走 {@link ArmorMaterialRules#durabilityBase}，
 * 自定义材料经 {@link ArmorMaterialRules#effectiveDurabilityBase} 查装配期反推并缓存的基数；
 * 分类统一走 {@link ArmorClass}（自定义未登记默认中甲）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：材料由枚举变为注册表条目，故所有入口改为接收
 * {@code ArmorMaterial}（注册表实例），由调用方用 {@code armorItem.getMaterial().value()} 取得。
 *
 * <p>设计定位：护甲材料修复量与基础耐久体系对齐。
 *
 * @author THEREDK
 */
public final class ArmorRepair {

    private ArmorRepair() {
    }

    /**
     * 计算单个修理材料对指定物品的修复量。
     *
     * @param stack 被修复的物品（结果格副本）
     * @return 修复量（≥1）；非护甲或无法确定基数的护甲返回 -1，调用方应回退原版 {@code maxDamage/4}
     */
    public static int getRepairPerMaterial(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return -1; // 非护甲：走原版 maxDamage/4
        }
        ArmorMaterial material = armor.getMaterial().value();
        double base = ArmorMaterialRules.effectiveDurabilityBase(material);
        if (base <= 0) {
            return -1; // 无法确定基数的护甲：走原版
        }
        ArmorClass clazz = ArmorClass.of(material);    // 未登记的自定义材料默认中甲
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double multiplier = loaded ? Config.COMMON.repairMaterialMultiplier.get() : 4.0;
        double amount = base * multiplier * clazz.ArmorTypeCoefficient() / clazz.repairEfficiencyCoefficient();
        return Math.max(1, (int) Math.floor(amount));
    }

    /** 护甲材料 → 铁砧修理花费系数登记表（未登记用全局配置，默认 1.0）。 */
    private static final Map<ArmorMaterial, Double> REPAIR_COST_COEFFICIENTS = new IdentityHashMap<>();

    /**
     * 为护甲材料登记铁砧修理花费系数（乘性，per-material 优先于全局配置）。
     *
     * <p>最终修理费用 = 原费用 × 系数（per-material 登记 &gt; 全局
     * {@code anvilRepairCostCoefficient}，默认 1.0）。给整合包作者调整个别材料的修理费：
     * 系数 &lt; 1 修理更便宜，&gt; 1 更昂贵，<b>0 免费</b>（与全局配置的 0 语义一致）。
     * 登记表<b>非线程安全</b>，建议加载期与 {@link ArmorClass#register} 同批调用。
     *
     * <p><b>哨兵区分</b>：{@code coefficient = 0} 是合法值（免费），直接登记；
     * 只有 <b>{@code null}</b>（或 {@code material == null}）才表示取消登记、
     * 回退全局配置 —— 避免与合法值 0 冲突。
     *
     * @param material    护甲材料（注册表内置条目或自定义实现）
     * @param coefficient 修理花费系数（0 免费；{@code null} 取消登记，回退全局配置）
     */
    public static void registerRepairCostCoefficient(ArmorMaterial material, @Nullable Double coefficient) {
        if (material != null && coefficient != null) {
            REPAIR_COST_COEFFICIENTS.put(material, coefficient);
        } else {
            REPAIR_COST_COEFFICIENTS.remove(material);
        }
    }

    /**
     * 铁砧修理花费系数：per-material 登记 &gt; 全局配置 {@code anvilRepairCostCoefficient}（默认 1.0）。
     *
     * @param material 护甲材料
     * @return 修理花费系数（≥ 0；0 表示免费）
     */
    public static double repairCostCoefficientOf(ArmorMaterial material) {
        Double registered = REPAIR_COST_COEFFICIENTS.get(material);
        if (registered != null) {
            return registered;
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return loaded ? Config.COMMON.anvilRepairCostCoefficient.get() : 1.0;
    }

    /**
     * 铁砧修理花费系数（按物品）：护甲按其材料查询，非护甲用全局配置。
     *
     * @param stack 被修复的物品
     * @return 修理花费系数（≥ 0；0 表示免费）
     */
    public static double repairCostCoefficientOf(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return repairCostCoefficientOf(armor.getMaterial().value());
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return loaded ? Config.COMMON.anvilRepairCostCoefficient.get() : 1.0;
    }
}
