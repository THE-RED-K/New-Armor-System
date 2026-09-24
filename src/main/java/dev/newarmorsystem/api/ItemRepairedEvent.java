package dev.newarmorsystem.api;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * 物品修复事件 —— 损坏（broken）物品的耐久被恢复到 maxDamage 以下、
 * broken 标记被清除的那一刻触发，不可取消。
 *
 * <p>本事件由 {@code dev.newarmorsystem.mixin.ItemStackMixin} 对
 * {@code ItemStack#setDamageValue(int)} 的 HEAD 注入在真正清除标记之后派发
 * （见 {@code newArmorSystem$clearBrokenOnRepair}）。铁砧材料修复、同类物品合并修复、
 * 经验修补（Mending）、命令/数据包修改等一切将耐久恢复到 maxDamage 以下的路径
 * 最终都会经过 {@code setDamageValue}，在此统一派发。
 *
 * <p><b>为什么不是 {@code LivingEvent}</b>：{@code setDamageValue} 只有目标耐久值一个
 * 参数，没有修理者/实体上下文，故本事件不带实体。需要修理者上下文的监听者
 * 请自行记录（如配合 NeoForge 原生 {@code AnvilUpdateEvent} / {@code PlayerEvent} 等）。
 *
 * <p><b>触发时机</b>：派发时 {@code nas:broken} 标记<b>已清除</b>
 * （{@link BrokenState#isBroken} 已为 false），而新的耐久值<b>尚未写入</b>
 * （{@code getStack().getDamageValue()} 仍返回旧值 maxDamage）；
 * 本事件携带的 {@link #getNewDamageValue()} 即将被写入。仅 broken → 正常的
 * 状态转换这一次派发 —— 非 broken 物品的耐久变化不触发本事件。
 *
 * <p><b>派发侧</b>：{@code setDamageValue} 的调用点可能存在于两个逻辑侧
 * （服务端结算、客户端预测等），监听者做世界修改前应自行用
 * {@code stack.level().isClientSide} 过滤客户端。
 *
 * <p><b>不可取消</b>：取消会破坏「broken ⇔ 耐久 ≥ maxDamage」的不变式
 * （标记已清除、耐久即将写入，回滚没有意义）。要<b>阻止</b>物品被修复，
 * 请在更上游干预（铁砧见 NeoForge 原生 {@code AnvilUpdateEvent}）。
 * 监听者典型用途：修复音效 / 提示、进阶任务触发器、修复增益 buff 等反馈型逻辑，
 * 与 {@link ItemBrokenEvent}（损坏方向）成对使用。
 *
 * @author THEREDK
 */
public class ItemRepairedEvent extends Event {

    /** 刚脱离 broken 状态的物品堆。 */
    private final ItemStack stack;

    /** 即将被写入的新耐久值（&lt; maxDamage）。 */
    private final int newDamageValue;

    /**
     * @param stack          刚脱离 broken 状态的物品堆（标记已清除，耐久值尚未写入）
     * @param newDamageValue 即将被写入的新耐久值（&lt; maxDamage）
     */
    public ItemRepairedEvent(ItemStack stack, int newDamageValue) {
        this.stack = stack;
        this.newDamageValue = newDamageValue;
    }

    /** @return 刚脱离 broken 状态的物品堆（派发时 {@code nas:broken} 标记已清除） */
    public ItemStack getStack() {
        return this.stack;
    }

    /**
     * @return 即将被写入的新耐久值（&lt; maxDamage）；派发时物品的
     * {@code getDamageValue()} 仍返回封顶值 maxDamage（旧值）
     */
    public int getNewDamageValue() {
        return this.newDamageValue;
    }
}
