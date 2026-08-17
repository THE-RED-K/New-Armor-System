package dev.newarmorsystem.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * 物品损坏事件 —— 物品耐久耗尽、转入损坏（broken）状态的那一刻触发，不可取消。
 *
 * <p>本事件在损坏状态系统（{@code Config.COMMON.brokenStateEnabled}，默认开启）把物品
 * 标记为 {@link BrokenState#isBroken} 时派发（见 {@code dev.newarmorsystem.mixin.ItemStackMixin}
 * 对 {@code ItemStack#hurtAndBreak(int, LivingEntity, Consumer)} 的注入：注入点在原版
 * {@code shrink(1)} 销毁指令处）。配置关闭（保留原版销毁）时不派发。
 *
 * <p><b>触发时机</b>：耐久已封顶于 maxDamage、{@code nas:broken} 标记已写入、
 * 原版 {@code ITEM_BREAK} 音效（{@code consumer.accept}）已播放，碎裂粒子在
 * 本事件之后广播。已损坏物品的重复损耗不会再次派发（早退分支），只派发
 * 状态转换那一次 —— 事件对"耐久 ≥ maxDamage 且带标记"的物品不再触发。
 *
 * <p><b>适用范围</b>：一切经 {@code hurtAndBreak} 损耗耐久的可损坏物品
 * （护甲、盾牌、工具、武器等），不限护甲。
 *
 * <p><b>不可取消</b>：取消会破坏「broken ⇔ 耐久 ≥ maxDamage」的不变式
 * （{@code setDamageValue} 的清标记链路依赖它）。要<b>阻止</b>物品损坏，
 * 请在更上游改写损耗量（护甲的受击损耗见 {@link ArmorHurtEvent#setNewDamage}）。
 * 监听者典型用途：损坏音效 / 提示、进阶任务触发器、损坏 debuff 等反馈型逻辑。
 *
 * @author THEREDK
 */
public class ItemBrokenEvent extends LivingEvent {

    /** 刚转入损坏状态的物品堆。 */
    private final ItemStack stack;

    /** 本次导致物品损坏的耐久损耗请求量（hurtAndBreak 的 amount 参数，未经耐久附魔减免）。 */
    private final int amount;

    /**
     * @param entity 使用该物品并令其损坏的实体（{@link #getEntity()} 返回它）
     * @param stack  刚转入损坏状态的物品堆
     * @param amount 本次导致物品损坏的耐久损耗请求量
     */
    public ItemBrokenEvent(LivingEntity entity, ItemStack stack, int amount) {
        super(entity);
        this.stack = stack;
        this.amount = amount;
    }

    /** 本事件不可取消：要阻止损坏请在上游改写损耗量（护甲见 {@link ArmorHurtEvent}）。 */
    @Override
    public boolean isCancelable() {
        return false;
    }

    /** @return 刚转入损坏状态的物品堆（调用时已带 {@code nas:broken} 标记） */
    public ItemStack getStack() {
        return this.stack;
    }

    /** @return 本次导致物品损坏的耐久损耗请求量（hurtAndBreak 的 amount 参数，未经耐久附魔减免） */
    public int getAmount() {
        return this.amount;
    }
}
