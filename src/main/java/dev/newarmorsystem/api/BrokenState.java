package dev.newarmorsystem.api;

import net.minecraft.world.item.ItemStack;

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
 * @author THEREDK
 */
public final class BrokenState {

    /** 标记 broken 状态的 NBT 键。 */
    public static final String BROKEN_TAG = "nas:broken";

    private BrokenState() {
    }

    /** 判断物品是否处于 broken 状态（空物品恒为 false）。 */
    public static boolean isBroken(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var tag = stack.getTag();
        return tag != null && tag.getBoolean(BROKEN_TAG);
    }

    /** 将物品标记为 broken（幂等）。调用方应确保耐久已归零。 */
    public static void markBroken(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(BROKEN_TAG, true);
    }

    /** 清除 broken 标记（幂等）。无标记时无操作；空物品忽略。 */
    public static void clearBroken(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        var tag = stack.getTag();
        if (tag != null && tag.contains(BROKEN_TAG)) {
            tag.remove(BROKEN_TAG);
        }
    }
}
