package dev.newarmorsystem.api;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 损坏状态（broken）工具。
 *
 * <p>物品耐久归零后不消失，而是打上 {@value #BROKEN_TAG} 标记进入 broken 状态。
 * broken 物品保留在背包/装备栏，但失去所有效果（属性、挖掘、使用、附魔），
 * 且不再损耗耐久（damage 封顶于 maxDamage，原版 {@code isBroken()} 判定成立）。
 *
 * <p><b>默认全覆盖</b>：一切可损坏物品都会进入损坏状态。若要让某些物品维持原版
 * 「耐久归零即爆掉消失」，把它们写进配置 {@code broken_state.brokenStateExemptItems}
 * （判定见 {@link #isExempt}）。
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

    /**
     * 判断物品是否处于 broken 状态（空物品恒为 false）。
     *
     * <p>先做 {@code isEmpty()} 纯短路（不触碰 NBT 与物品注册表），保证启动期与
     * {@code ItemStack} 构造器路径（会经 {@code setDamageValue} 早期调用）安全。
     *
     * @param stack 待检查的物品堆
     * @return 是否处于 broken 状态；空物品恒为 {@code false}
     */
    public static boolean isBroken(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var tag = stack.getTag();
        return tag != null && tag.getBoolean(BROKEN_TAG);
    }

    /**
     * 判断物品是否被配置声明为「不进入损坏状态」。
     *
     * <p>由 {@code broken_state.brokenStateExemptItems}（物品 ID 列表，<b>默认空</b>）声明：
     * 列表中的物品在耐久归零时<b>不进入</b> broken 状态，而是按原版爆掉消失
     * （见 {@code dev.newarmorsystem.mixin.ItemStackMixin} 的 {@code hurtAndBreak} 注入）。
     * 空列表 = 所有可损坏物品都会进入损坏状态（默认行为）。
     *
     * <p><b>不做解析缓存</b>：本方法只在耐久归零那一刻被调用（不在高频路径），
     * 逐次解析可天然跟随配置重载，不会因缓存失效而在重载后仍读旧值。
     * 无法解析的 ID 直接忽略（配置的校验器已挡掉大部分手误，此处只做兜底）。
     *
     * @param stack 待检查的物品堆
     * @return true = 该物品不走损坏状态（按原版爆掉）；空物品/配置未加载恒为 false
     */
    public static boolean isExempt(ItemStack stack) {
        if (stack.isEmpty() || !Config.COMMON_SPEC.isLoaded()) {
            return false;
        }
        List<? extends String> ids = Config.COMMON.brokenStateExemptItems.get();
        if (ids.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        for (String id : ids) {
            if (key.equals(ResourceLocation.tryParse(id))) {   // tryParse 失败返回 null，equals(null) 为 false
                return true;
            }
        }
        return false;
    }

    /**
     * 将物品标记为 broken（幂等）。调用方应确保耐久已归零。
     *
     * @param stack 待标记的物品堆
     */
    public static void markBroken(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(BROKEN_TAG, true);
    }

    /**
     * 清除 broken 标记（幂等）。无标记时无操作；空物品忽略。
     *
     * @param stack 待清除标记的物品堆
     */
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
