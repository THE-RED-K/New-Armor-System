package dev.newarmorsystem.api;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * 荆棘反伤事件 —— 护甲损失耐久产生的反伤在真正施加之前触发，可改写反伤量 / 目标或取消。
 *
 * <p>本事件由 {@code Inventory#hurtArmor(DamageSource, float, int[])} 的
 * {@code @Overwrite} 方法体在<b>按件累积完反伤量之后、对目标施加 {@code thorns}
 * 伤害之前</b>派发（见 {@code dev.newarmorsystem.mixin.InventoryMixin}）。
 * 没有被监听时行为与不派发完全一致：反伤量 = 各件
 * （{@link DamageReflection#of} 材料比例 + 荆棘附魔等级 × 60%）× 该件实际损耗的耐久
 * 之和，目标与伤害类型也与公式路径一致。
 *
 * <p><b>默认目标</b>：任一受损护甲开启 {@link DamageReflection#reflectsToSelf}
 * （诅咒类）时为<b>穿戴者自己</b>（无论伤害来源是谁）；否则为本次伤害来源实体
 * （{@code DamageSource#getEntity()}，可为玩家自己）；来源不存在或不是
 * {@link LivingEntity} 时为 {@code null}（默认不反伤）。
 *
 * <p><b>可改写</b>：
 * <ul>
 *   <li>{@link #setAmount(float)}：反伤总量（负数按 0 处理）；</li>
 *   <li>{@link #setTarget(LivingEntity)}：反伤目标（可指向第三方实体；
 *       {@code null} = 本次不反伤）；</li>
 *   <li>{@link #setCanceled(boolean)}：取消后本次不施加反伤。</li>
 * </ul>
 *
 * <p><b>防循环保证</b>：反伤伤害本身是 {@code thorns} 类型，而 {@code hurtArmor}
 * 对 {@code thorns} 来源不累积反伤（见 {@code InventoryMixin}），故反伤链路不会
 * 再次触发本事件，监听者无需自行防环。
 *
 * <p><b>反伤伤害归因</b>：无论目标被改写为谁，施加的 {@code DamageSource}
 * 恒为 {@code thorns(穿戴者)}（{@code getEntity()} 返回穿戴者）—— 伤害归因于
 * 护甲穿戴者，与默认路径一致。
 *
 * <p>仅服务端伤害结算链路派发（主线程）。
 *
 * @author THEREDK
 */
public class ArmorReflectEvent extends LivingEvent {

    /** 本次造成护甲损耗（并触发反伤）的伤害来源。 */
    private final DamageSource source;

    /** 每件护甲对反伤总量的贡献（按槽位索引，只读视图）。 */
    private final Map<EquipmentSlot, Float> contributions;

    /** 派发时的诅咒类"必定反伤穿戴者"标记（默认目标已据此确定）。 */
    private final boolean reflectToSelf;

    /** 反伤总量（可写）。 */
    private float amount;

    /** 反伤目标（可写；null = 本次不反伤）。 */
    @Nullable
    private LivingEntity target;

    /**
     * @param entity        穿戴者（反伤伤害的攻击者，{@link #getEntity()} 返回它）
     * @param source        造成护甲损耗的伤害来源
     * @param contributions 每件护甲的反伤贡献（按槽位索引，非空）
     * @param reflectToSelf 诅咒类"必定反伤穿戴者"标记
     * @param amount        公式累积出的反伤总量（&gt; 0）
     * @param target        默认反伤目标（可为 {@code null} = 默认不反伤）
     */
    public ArmorReflectEvent(LivingEntity entity, DamageSource source,
                             Map<EquipmentSlot, Float> contributions, boolean reflectToSelf,
                             float amount, @Nullable LivingEntity target) {
        super(entity);
        this.source = source;
        this.contributions = contributions;
        this.reflectToSelf = reflectToSelf;
        this.amount = amount;
        this.target = target;
    }

    /** 本事件可取消：取消后本次不施加反伤。 */
    @Override
    public boolean isCancelable() {
        return true;
    }

    /** @return 本次造成护甲损耗（并触发反伤）的伤害来源 */
    public DamageSource getDamageSource() {
        return this.source;
    }

    /**
     * 每件护甲对反伤总量的贡献（(材料比例 + 荆棘等级 × 60%) × 该件实际损耗的耐久）。
     *
     * @return 按槽位索引的贡献表（只读视图；仅含实际产生反伤的护甲）
     */
    public Map<EquipmentSlot, Float> getContributions() {
        return Collections.unmodifiableMap(this.contributions);
    }

    /** @return 派发时是否有护甲开启了"必定反伤穿戴者"（诅咒类） */
    public boolean isReflectToSelf() {
        return this.reflectToSelf;
    }

    /** @return 当前反伤总量（恒 ≥ 0） */
    public float getAmount() {
        return this.amount;
    }

    /**
     * 改写反伤总量。
     *
     * @param amount 新的反伤总量（负数按 0 处理）
     */
    public void setAmount(float amount) {
        this.amount = Math.max(0.0F, amount);
    }

    /**
     * 当前反伤目标。
     *
     * @return 反伤目标；{@code null} 表示本次不反伤
     */
    @Nullable
    public LivingEntity getTarget() {
        return this.target;
    }

    /**
     * 改写反伤目标（可指向穿戴者、伤害来源以外的第三方实体）。
     *
     * @param target 新的反伤目标；{@code null} 表示本次不反伤
     */
    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }
}
