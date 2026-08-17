package dev.newarmorsystem.api;

import net.minecraftforge.eventbus.api.Event;

/**
 * 护甲减伤结算事件 —— 新护甲减伤公式的结果在返回原版伤害管线之前触发，可改写结算后伤害。
 *
 * <p>本事件由 {@code CombatRules#getDamageAfterAbsorb(float, float, float)} 的
 * {@code @Overwrite} 方法体在 {@link NewCombatRules#getDamageAfterArmor} 公式
 * 结算完成之后、返回之前派发（见 {@code dev.newarmorsystem.mixin.CombatRulesMixin}）。
 * 没有被监听时行为与不派发完全一致：返回值即公式结果。
 *
 * <p><b>为什么不是 {@code LivingEvent}</b>：{@code getDamageAfterAbsorb} 是
 * {@code LivingEntity#getDamageAfterArmorAbsorb} 调用的静态工具方法，调用点没有
 * 受击实体上下文，故本事件不带实体。需要实体的监听者请配合 Forge 原生
 * {@code LivingHurtEvent} / {@code LivingDamageEvent} 使用 —— 两者分别在本公式
 * <b>之前</b>（可改原始伤害）与<b>之后</b>（可改最终伤害）触发。
 *
 * <p><b>不可取消</b>：「绕过护甲直接吃满伤害」用 {@link #setNewDamage(float)}
 * 设为 {@link #getDamage()}（原始伤害）即可表达；{@code newDamage = 0} 表示全额减免。
 *
 * <p>公式本身（{@code @Overwrite} 唯一接管）不受本事件影响：事件只开放
 * <b>结果出口</b>，{@link #getOriginalDamage()} 恒为未派发时的公式返回值。
 *
 * @author THEREDK
 */
public class ArmorReduceEvent extends Event {

    /** 护甲结算前的原始伤害。 */
    private final float damage;

    /** 目标护甲值。 */
    private final float armor;

    /** 目标盔甲韧性。 */
    private final float toughness;

    /** 公式结算结果（未派发事件时的返回值）。 */
    private final float originalDamage;

    /** 当前生效的结算后伤害（可写）。 */
    private float newDamage;

    /**
     * @param damage         护甲结算前的原始伤害
     * @param armor          目标护甲值
     * @param toughness      目标盔甲韧性
     * @param originalDamage 公式结算结果（{@code newDamage} 初值同此）
     */
    public ArmorReduceEvent(float damage, float armor, float toughness, float originalDamage) {
        this.damage = damage;
        this.armor = armor;
        this.toughness = toughness;
        this.originalDamage = originalDamage;
        this.newDamage = originalDamage;
    }

    /** 本事件不可取消：改写结果请用 {@link #setNewDamage(float)}。 */
    @Override
    public boolean isCancelable() {
        return false;
    }

    /** @return 护甲结算前的原始伤害 */
    public float getDamage() {
        return this.damage;
    }

    /** @return 目标护甲值 */
    public float getArmor() {
        return this.armor;
    }

    /** @return 目标盔甲韧性 */
    public float getToughness() {
        return this.toughness;
    }

    /** @return 公式结算结果（未派发事件时的返回值） */
    public float getOriginalDamage() {
        return this.originalDamage;
    }

    /** @return 当前生效的结算后伤害（等于 {@link #getOriginalDamage()} 时说明未被改写） */
    public float getNewDamage() {
        return this.newDamage;
    }

    /**
     * 改写护甲结算后的伤害。
     *
     * @param newDamage 新的结算后伤害（负数按 0 处理）
     */
    public void setNewDamage(float newDamage) {
        this.newDamage = Math.max(0.0F, newDamage);
    }
}
