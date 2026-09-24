package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.ArmorRepair;
import dev.newarmorsystem.api.Config;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
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
 * <p><b>1. 单个材料修复量</b>：原版材料修复分支
 * {@code Math.min(damage, getMaxDamage() / 4)}，每个修理材料最多修复
 * {@code maxDamage/4} 点耐久。本模组对护甲改为：单个材料修复量 =
 * 耐久基数 × 4 × AT / 修理效率系数（见 {@link ArmorRepair}），且不超过当前损坏值。
 * 实现上重定向材料修复分支的两处 {@code getMaxDamage()} 调用
 * （ordinal 0/1，经字节码核对确为分支内前两处调用，合并/附魔分支不受影响）：
 * 护甲返回 {@code 修复量 × 4}，使原表达式 {@code getMaxDamage() / 4} 恰为自定义修复量。
 *
 * <p><b>2. 经验费用规则（消耗经验等级 = 消耗材料数）</b>：1.21 起原版把历史修理惩罚
 * 由 {@code ItemStack#getBaseRepairCost()} 改为 <b>数据组件</b>
 * {@code DataComponents.REPAIR_COST}（{@code getOrDefault} 读取、{@code set} 写回），
 * 故重定向目标相应从 {@code getBaseRepairCost()} 改为
 * {@code ItemStack#getOrDefault(DataComponentType, Object)}：
 * <ul>
 *   <li>读取端恒返回 0 → 累加项 {@code j} 恒为 0，且写回端的初值也为 0，
 *       经 {@code calculateIncreasedRepairCost(0)=1} 后稳定为 1，惩罚永不增长；</li>
 *   <li>总费用 {@code cost = j + i} 中 {@code i} 原版按每消耗 1 个材料 {@code ++i}，
 *       故纯材料修复时费用恰等于消耗材料数，可无限次修理。</li>
 * </ul>
 *
 * <p><b>3. "过于昂贵"阈值</b>：{@code createResult} 内 {@code DataSlot#get()} 仅用于
 * {@code >= 40} 的两处判断（经字节码核对：createResult 内共 2 处），
 * 与 1.20.1 的处理一致：移除模式下返回 0，保留模式下平移为
 * {@code 真实值 − 阈值 + 40}。
 *
 * <p><b>4. 修理花费系数</b>：{@code DataSlot#set(int)} 写入前乘 per-material / 全局系数，
 * 使 UI 显示、实际扣费与阈值判断读到同一个缩放值。
 *
 * <p>不要在此处添加 {@code remap = false}。
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
        // 护甲返回修复量 × 4，抵消原表达式 getMaxDamage() / 4 中的 / 4
        return custom >= 0 ? custom * 4 : stack.getMaxDamage();
    }

    /**
     * 忽略历史修理惩罚：{@code createResult} 内所有 {@code REPAIR_COST} 读取恒返回 0。
     *
     * <p>原版 {@code j += itemstack.getOrDefault(REPAIR_COST, 0) + itemstack2.getOrDefault(REPAIR_COST, 0)}
     * 把两件物品累积的修理惩罚计入总费用，且末尾 {@code set(REPAIR_COST, 增长后的值)}
     * 让惩罚越滚越大，最终必然"过于昂贵"。本重定向使 {@code j} 恒为 0，
     * 同时写回端初值也恒为 0，惩罚永不累积。
     *
     * <p>只对 {@code REPAIR_COST} 生效：createResult 内的 {@code getOrDefault} 调用
     * 经字节码核对全部为 REPAIR_COST，此处仍做类型判断以防御将来变更。
     *
     * @author THEREDK
     * @reason 无经验惩罚，修理费用 = 消耗材料数
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object newArmorSystem$ignoreRepairPenalty(ItemStack stack, DataComponentType<?> type, Object fallback) {
        if (type == DataComponents.REPAIR_COST && Config.COMMON_SPEC.isLoaded()
                && Config.COMMON.anvilRepairCostRule.get()) {
            return 0;
        }
        return newArmorSystem$getOrDefaultRaw(stack, type, fallback);
    }

    /** 原样转发 {@code getOrDefault}（泛型方法需按擦除签名转发）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object newArmorSystem$getOrDefaultRaw(ItemStack stack, DataComponentType<?> type, Object fallback) {
        return stack.getOrDefault((DataComponentType) type, fallback);
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
     * 真实费用仍由 {@code cost.set(...)} 写入 DataSlot，UI 显示与实际扣费不受影响；
     * handler 内 {@code slot.get()} 为普通调用，不会再次被本重定向拦截。
     *
     * @author THEREDK
     * @reason 可配置"过于昂贵"阈值；移除模式下不读阈值配置
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;get()I"))
    private int newArmorSystem$applyTooExpensiveCap(DataSlot slot) {
        if (Config.COMMON_SPEC.isLoaded()) {
            if (Config.COMMON.anvilRepairCostRule.get()) {
                return 0; // 移除模式：不读阈值配置，恒不触发 >= 40
            }
            int threshold = Config.COMMON.tooExpensiveThreshold.get();
            return slot.get() - threshold + 40; // 平移：>= 40 判断等价于真实费用 >= 阈值
        }
        return slot.get();
    }

    /**
     * 铁砧修理花费系数：{@code createResult} 内所有 {@code DataSlot#set} 调用
     * （含材料修复最终费用 {@code cost.set(j + i)}）写入前乘 per-material / 全局系数。
     *
     * <p>系数写在 set 处而非 get 处：{@code cost} 的 get 已被
     * {@link #newArmorSystem$applyTooExpensiveCap} 重定向（阈值平移），若乘在 get
     * 会与阈值逻辑纠缠；set 处缩放后，UI 显示、实际扣费与后续阈值判断读到的
     * 是同一个缩放值，三者自洽。默认 1.0 时行为与原版一致。
     * handler 内 {@code slot.set(...)} 为普通调用，不会再次被本重定向拦截。
     *
     * <p>签名说明：{@code DataSlot#set} 是实例方法，{@code @Redirect} 的 handler 首参必须是
     * <b>被调用对象（receiver，即 {@code slot} 本身）</b>而非调用者；故本 handler 为
     * 实例方法（{@code this} 即 AnvilMenu）。读取被修复物时把 {@code this} 强转为
     * 公开父类 {@link AbstractContainerMenu} 调 {@code getSlot(0)}
     * （运行期 {@code this} 正是 AnvilMenu，继承关系成立），
     * 避开 {@code @Shadow} 父类私有字段/方法的解析限制与运行期风险。
     *
     * @author THEREDK
     * @reason 铁砧修理花费系数（可配置 + per-material 登记）
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V"))
    private void newArmorSystem$applyRepairCostCoefficient(DataSlot slot, int value) {
        // 槽位 0 = 被修复物（对应父类私有 inputSlots），经公开 getSlot 取物品查 per-material 系数
        double coefficient = ArmorRepair.repairCostCoefficientOf(
                ((AbstractContainerMenu) (Object) this).getSlot(0).getItem());
        slot.set(Math.max(0, (int) Math.round(value * coefficient)));
    }
}
