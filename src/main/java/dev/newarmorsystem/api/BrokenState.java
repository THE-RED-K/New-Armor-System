package dev.newarmorsystem.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 损坏状态（broken）工具。
 *
 * <p>物品耐久归零后不消失，而是打上 {@value #BROKEN_TAG} 标记进入 broken 状态。
 * broken 物品保留在背包/装备栏，但失去所有效果（属性、挖掘、使用、附魔），
 * 且不再损耗耐久（damage 封顶于 maxDamage，原版 {@code isBroken()} 判定成立）。
 *
 * <p>任何将耐久恢复到 maxDamage 以下的修理（铁砧材料/同类合并、经验修补、
 * 命令等）都会清除本标记恢复物品功能，保证不变式：broken ⇔ 耐久 ≥ maxDamage。
 *
 * <p><b>1.20.1 → 1.21.1</b>：标记载体由裸 NBT（{@code ItemStack#getTag/getOrCreateTag}）
 * 迁移到 1.21 的数据组件 {@link DataComponents#CUSTOM_DATA}
 * （{@link CustomData}）。键名与布尔语义不变，旧存档中的 {@code nas:broken} 标记
 * 位于 {@code minecraft:custom_data} 下，语义等价。
 *
 * @author THEREDK
 */
public final class BrokenState {

    /** 标记 broken 状态的 NBT 键。 */
    public static final String BROKEN_TAG = "nas:broken";

    private BrokenState() {
    }

    /**
     * 判断物品是否处于 broken 状态（空物品恒为 false）。
     *
     * <p><b>顺序与成本都很关键</b>：
     * <ul>
     *   <li>先做 {@code isEmpty()} 纯短路（不触碰组件与物品注册表），保证启动期
     *       与 {@code ItemStack} 构造器路径（会经 {@code setDamageValue} 早期调用）安全；</li>
     *   <li>读组件用 {@code contains(key)} 而非 {@code copyTag()} —— 后者会<b>深拷贝整个 NBT</b>，
     *       而本方法位于高频路径（护甲受损、附魔效果迭代、挖掘速度判定等），
     *       深拷贝的分配开销不可接受；{@code contains} 直接查原始 tag，零分配且未废弃
     *       （{@code getUnsafe()} 在 1.21.1 已 {@code @Deprecated}，不宜使用）。</li>
     *   <li>语义依赖不变式：标记只以 {@code true} 写入（{@link #markBroken}），
     *       清除时直接移除键（{@link #clearBroken}）—— 故<b>「键存在」即 broken</b>，
     *       不存在 {@code nas:broken = false} 的写法（1.20.1 同此约定）。</li>
     * </ul>
     *
     * @param stack 待检查的物品堆
     * @return 是否处于 broken 状态；空物品恒为 {@code false}
     */
    public static boolean isBroken(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(BROKEN_TAG);
    }

    /** 将物品标记为 broken（幂等）。调用方应确保耐久已归零。 */
    public static void markBroken(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(BROKEN_TAG, true));
    }

    /** 清除 broken 标记（幂等）。无标记时无操作；空物品忽略。 */
    public static void clearBroken(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(BROKEN_TAG)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BROKEN_TAG));
        }
    }
}
