package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.ArmorRepair;
import dev.newarmorsystem.api.Config;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 重写铁砧材料修复的"单个材料修复量"与"经验费用规则"。
 *
 * <p><b>1. 单个材料修复量</b>：原版 {@code AnvilMenu#createResult} 材料修复分支
 * {@code Math.min(damage, maxDamage / 4)}，每个修理材料最多修复
 * {@code maxDamage/4} 点耐久。
 * 本模组对护甲改为：单个材料修复量 = 耐久基数 × 4 × AT / 修理效率系数（见 {@link ArmorRepair}），
 * 且不超过当前损坏值（{@code Math.min} 天然约束）。
 * 实现上重定向材料修复分支的两处 {@code ItemStack#getMaxDamage()} 调用
 * （ordinal 0/1 均位于材料修复分支，合并/附魔分支不受影响）：
 * 护甲返回 {@code 修复量 × 4}，使原表达式 {@code getMaxDamage() / 4} 恰为自定义修复量；
 * 非护甲返回原值，行为与原版完全一致。
 *
 * <p><b>2. 经验费用规则（消耗经验等级 = 消耗材料数）</b>：
 * <ul>
 *   <li>原版材料修复分支本就按每消耗 1 个材料 {@code ++i} 计费；
 *       但总费用 {@code cost = j + i} 中 {@code j} 为两件物品历史修理惩罚之和
 *       （RepairCost 每次操作按 {@code 旧值×2+1} 递增），累积后必然"过于昂贵"。</li>
 *   <li>重定向 {@code createResult} 内所有 {@code getBaseRepairCost()} 返回 0：
 *       {@code j} 恒为 0，末尾 {@code setRepairCost} 的输入也恒为 0，
 *       惩罚不再累积 —— 纯材料修复时费用恰等于消耗材料数，可无限次修理。</li>
 *   <li>重定向 {@code createResult} 内 {@code DataSlot#get()} 返回钳制值，
 *       使"过于昂贵"的 {@code cost >= 40} 两处判断恒不成立，移除 40 级上限。</li>
 * </ul>
 *
 * @author THEREDK
 * @reason 护甲铁砧材料修复量重定义 + 修理费用规则
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    /** 材料修复分支：初始计算修复量（第一个 getMaxDamage 调用）。 */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxDamage()I", ordinal = 0))
    private int newArmorSystem$repairPerMaterialInit(ItemStack stack) {
        int custom = ArmorRepair.getRepairPerMaterial(stack);
        return custom >= 0 ? custom * 4 : stack.getMaxDamage();
    }

    /** 材料修复分支：循环内每消耗一个材料重算修复量（第二个 getMaxDamage 调用）。 */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxDamage()I", ordinal = 1))
    private int newArmorSystem$repairPerMaterialLoop(ItemStack stack) {
        int custom = ArmorRepair.getRepairPerMaterial(stack);
        return custom >= 0 ? custom * 4 : stack.getMaxDamage();// 护甲返回修复量 × 4，抵消原表达式 {@code getMaxDamage() / 4}中的 / 4.
    }

    /**
     * 忽略历史修理惩罚：{@code createResult} 内所有 {@code getBaseRepairCost()} 一律返回 0。
     * <p>
     * 原版 {@code j += itemstack.getBaseRepairCost() + itemstack2.getBaseRepairCost()}
     * 把两件物品累积的修理惩罚计入总费用，且末尾 {@code setRepairCost(旧值×2+1)}
     * 让惩罚越滚越大，最终必然"过于昂贵"、无法再修。
     * 本重定向使 {@code j} 恒为 0，同时末尾 {@code setRepairCost} 的输入 k3 也恒为 0
     * （{@code calculateIncreasedRepairCost(0) = 1}，后续不再递增），
     * 惩罚永不累积 —— 材料修复分支原版 {@code ++i} 每消耗 1 个材料 +1 级，
     * 故最终费用恰等于消耗材料数，可无限次修理。
     * <p>
     * 配置关闭时返回原值，行为与原版一致。
     *
     * @author THEREDK
     * @reason 无经验惩罚，修理费用 = 消耗材料数
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getBaseRepairCost()I"))
    private int newArmorSystem$ignoreRepairPenalty(ItemStack stack) {
        if (Config.COMMON_SPEC.isLoaded() && Config.COMMON.anvilRepairCostRule.get()) {
            return 0;
        }
        return stack.getBaseRepairCost();
    }

    /**
     * 可配置"过于昂贵"阈值：{@code createResult} 内 {@code DataSlot#get()} 的返回值
     * 仅用于 {@code cost >= 40} 的两处判断（纯重命名钳制为 39、非创造模式结果置空）。
     * <ul>
     *   <li><b>移除模式</b>（{@code anvilRepairCostRule = true}）：返回固定极小值 0，
     *       {@code 0 >= 40} 恒不成立 —— 阈值配置 {@code tooExpensiveThreshold}
     *       <b>必然不生效、直接不读</b>，完全移除"过于昂贵"。</li>
     *   <li><b>保留模式</b>（{@code anvilRepairCostRule = false}）：读取阈值配置，
     *       将返回值平移为 {@code 真实值 − 阈值 + 40}，使原版 {@code >= 40} 判断
     *       等价于 {@code 真实费用 >= 阈值}（阈值默认 40，行为与原版一致）。</li>
     * </ul>
     * 真实费用仍由 {@code cost.set(j + i)} 写入 DataSlot，UI 显示与实际扣费不受影响；
     * handler 内 {@code cost.get()} 为普通调用，不会再次被本重定向拦截。
     *
     * @author THEREDK
     * @reason 可配置"过于昂贵"阈值；移除模式下不读阈值配置
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;get()I"))
    private int newArmorSystem$applyTooExpensiveCap(DataSlot cost) {
        if (Config.COMMON_SPEC.isLoaded()) {
            if (Config.COMMON.anvilRepairCostRule.get()) {
                return 0; // 移除模式：不读阈值配置，恒不触发 >= 40
            }
            int threshold = Config.COMMON.tooExpensiveThreshold.get();
            return cost.get() - threshold + 40; // 平移：>= 40 判断等价于真实费用 >= 阈值
        }
        return cost.get();
    }

    /**
     * 铁砧修理花费系数：{@code createResult} 内所有 {@code DataSlot#set} 调用
     * （含材料修复最终费用 {@code cost.set(j + i)}）写入前乘 per-material / 全局系数。
     *
     * <p>系数写在 set 处而非 get 处：{@code cost} 的 get 已被
     * {@link #newArmorSystem$applyTooExpensiveCap} 重定向（阈值平移），若乘在 get
     * 会与阈值逻辑纠缠；set 处缩放后，UI 显示、实际扣费与后续阈值判断读到的
     * 是同一个缩放值，三者自洽。
     * 系数 = per-material 登记（{@link ArmorRepair#registerRepairCostCoefficient}）
     * &gt; 全局配置 {@code anvil_repair_cost.anvilRepairCostCoefficient}（默认 1.0）；
     * 0 使修理免费。默认 1.0 时行为与原版一致。
     * handler 内 {@code cost.set(...)} 为普通调用，不会再次被本重定向拦截。
     *
     * <p>签名说明：{@code DataSlot#set} 是实例方法，@Redirect 的 handler 首参必须是
     * <b>被调用对象（receiver，即 {@code cost} 本身）</b>而非调用者；故本 handler 为
     * 实例方法（{@code this} 即 AnvilMenu）。读取被修复物时把 {@code this} 强转为
     * 公开父类 {@link AbstractContainerMenu} 调 {@code getSlot(0)}（运行期 {@code this}
     * 正是 AnvilMenu，继承关系成立），避开 {@code @Shadow} 父类私有字段/方法的
     * 解析限制与运行期风险。
     *
     * @author THEREDK
     * @reason 铁砧修理花费系数（可配置 + per-material 登记）
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V"))
    private void newArmorSystem$applyRepairCostCoefficient(DataSlot cost, int value) {
        // 槽位 0 = 被修复物（对应父类私有 inputSlots），经公开 getSlot 取物品查 per-material 系数
        double coefficient = ArmorRepair.repairCostCoefficientOf(
                ((AbstractContainerMenu) (Object) this).getSlot(0).getItem());
        cost.set(Math.max(0, (int) Math.round(value * coefficient)));
    }
}
