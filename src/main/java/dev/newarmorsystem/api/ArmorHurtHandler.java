package dev.newarmorsystem.api;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/**
 * 用新护甲耐久损耗公式接管原版 {@code LivingEntity#doHurtEquipment(DamageSource, float, EquipmentSlot...)}
 * 中的损耗计算，并驱动反伤系统。
 *
 * <p>由 {@code dev.newarmorsystem.mixin.LivingEntityHurtEquipmentMixin} 在 {@code doHurtEquipment}
 * 的 {@code HEAD} 处调用；返回 {@code true} 时由该 Mixin 取消原方法（本次损耗已全部由本类完成）。
 *
 * <p>原版损耗：{@code pDamage /= 4.0F}（每件护甲扣 {@code max(原始伤害 / 4, 1)} 点耐久，与韧性无关）。
 * 本模组：每件护甲按自身提供的韧性（attribute modifier）代入
 * {@link NewCombatRules#getDurabilityLoss(float, float, boolean)}，韧性越高损耗越小。
 *
 * <p><b>1.20.1 → 1.21.1 的落点迁移</b>：1.20.1 的损耗计算在
 * {@code Inventory#hurtArmor(DamageSource, float, int[])}（仅玩家、按槽位下标索引），
 * 1.21.1 已上移到 {@link LivingEntity#doHurtEquipment}（按 {@link EquipmentSlot} 索引），
 * 故接管点随之改变；<b>副作用</b>：作用对象由"玩家"放宽为"所有穿戴护甲的生物"
 * （狼、马等调用方同样经过本方法），这是落点变更的自然结果，公式本身与材料绑定、与穿戴者无关。
 *
 * <p><b>与 NeoForge 原生 {@code ArmorHurtEvent} 的关系</b>：1.21.1 的 NeoForge 已提供
 * {@code ArmorHurtEvent}（1.20.1 时不存在，故当时本模组自备了同字段的事件类），
 * 因此本类<b>不再自备护甲受损事件</b>，而是<b>直接派发原生事件</b>：
 * <ul>
 *   <li>事件里每件的 {@code originalDamage} = 本模组公式算出的扣血量
 *       （而非原版的 {@code (int)max(1, damage/4)}），{@code newDamage} 初值同此；</li>
 *   <li>监听者可用 {@code setNewDamage(slot, v)} 逐件改写，或取消本次损耗；</li>
 *   <li>取消后<b>不扣任何护甲耐久、也不产生反伤</b>（与 1.20.1 语义一致）。</li>
 * </ul>
 * 事件之后的耐久施加与 NeoForge 原实现保持一致
 * （{@code entry.armorItemStack.hurtAndBreak((int) entry.newDamage, entity, slot)}，
 * 见 {@code CommonHooks#onArmorHurt}）—— 因为进入本类的入口已早于该调用，故此处复刻该施加语句。
 *
 * <p><b>反伤</b>：护甲实际损失耐久时（{@code actualLoss > 0}），按
 * 反伤比例 × 损失耐久度累积反伤，在耐久施加完成后派发 {@link ArmorReflectEvent} 并施加
 * {@code thorns} 类型反伤 —— 顺序与 1.20.1 完全一致。反伤比例 =
 * {@link DamageReflection#of} 的材料比例 + 荆棘附魔等级 × 60%
 * （每级 +60%，必定反伤、无概率判定；原版荆棘机制已由
 * {@code dev.newarmorsystem.mixin.ThornsEnchantmentMixin} 禁用）。反伤目标：
 * 默认对伤害来源实体（{@code DamageSource#getEntity()}，可为玩家自己）；
 * 若受损失护甲中任一件开启了 {@link DamageReflection#reflectsToSelf}
 * （诅咒类护甲），则<b>必定反伤玩家自己</b>（穿戴者），而不是反伤目标。
 * 低伤害攻击让护甲不掉耐久 → 无反伤，因此反伤对小怪无效（刻意设计）。
 * {@code thorns} 反伤伤害不再触发反伤，防止反伤护甲互击时无限循环。
 *
 * @author THEREDK
 */
public final class ArmorHurtHandler {

    private ArmorHurtHandler() {
    }

    /**
     * 按模组公式结算本次护甲损耗，并驱动原生受损事件与反伤链路。
     *
     * @param entity 护甲所属实体
     * @param source 造成损耗的伤害来源
     * @param damage 损耗前的原始伤害（未经 {@code /4}）
     * @param slots  本次参与损耗的护甲槽位
     * @return {@code true} = 本次损耗已由本类完全接管（调用方应取消原方法）；
     * {@code false} = 无任何可受损护甲，交回原版 / NeoForge 路径处理
     * （原样派发空的原生事件，行为与不接管时完全一致）
     */
    public static boolean apply(LivingEntity entity, DamageSource source, float damage, EquipmentSlot[] slots) {
        if (damage <= 0.0F || slots == null || slots.length == 0) {
            return false;
        }
        // 反伤伤害（thorns 类型）本身不累积反伤，防止反伤护甲互击时无限循环
        boolean isReflectionDamage = source.is(DamageTypes.THORNS);
        // 每件护甲对反伤总量的贡献（比例 × 该件实际损耗），派发 ArmorReflectEvent 用
        EnumMap<EquipmentSlot, Float> reflections = new EnumMap<>(EquipmentSlot.class);

        // 1) 采集本次参与损耗的护甲（筛选条件与 NeoForge onArmorHurt 一致），按模组公式算出各自扣血量
        EnumMap<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entries = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : slots) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem) || !stack.canBeHurtBy(source)) {
                continue;
            }
            float loss = NewCombatRules.getDurabilityLoss(damage,
                    NewCombatRules.getPieceToughness(stack, slot),
                    NewCombatRules.hasUnbreaking(stack));
            entries.put(slot, new ArmorHurtEvent.ArmorEntry(stack, loss));
        }
        if (entries.isEmpty()) {
            return false;
        }

        // 2) 派发 NeoForge 原生护甲受损事件：监听者可按槽位改写扣血量，取消则本次不扣耐久、也不产生反伤
        ArmorHurtEvent event = new ArmorHurtEvent(entries, entity, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return true;
        }

        // 3) 逐件施加耐久损耗（取事件改写后的值向下取整，与 NeoForge 原实现一致），同时累积反伤
        boolean reflectToSelf = false;
        float reflectedDamage = 0.0F;
        for (Map.Entry<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entry : entries.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ArmorHurtEvent.ArmorEntry armorEntry = entry.getValue();
            int actualLoss = (int) armorEntry.newDamage;
            if (actualLoss <= 0) {
                continue;
            }
            ItemStack stack = armorEntry.armorItemStack;
            if (stack.getItem() instanceof ArmorItem armorItem) {
                if (!isReflectionDamage) {
                    ArmorMaterial material = armorItem.getMaterial().value();
                    if (DamageReflection.reflectsToSelf(material)) {
                        reflectToSelf = true;
                    }
                    // 反伤比例 = 材料反伤比例 + 荆棘附魔每级 +60%（必定反伤，无概率）
                    double ratio = DamageReflection.of(material);
                    int thornsLevel = NewCombatRules.getThornsLevel(stack);
                    if (thornsLevel > 0) {
                        ratio += 0.6 * thornsLevel;
                    }
                    float contribution = (float) (ratio * actualLoss);
                    reflections.put(slot, contribution);
                    reflectedDamage += contribution;
                }
                stack.hurtAndBreak(actualLoss, entity, slot);
            }
        }

        // 4) 护甲损失耐久后施加累积反伤（低伤害不掉耐久 → 无反伤，对小怪无效是刻意设计）
        if (reflectedDamage <= 0.0F) {
            return true;
        }
        // 默认反伤目标：诅咒类护甲必定反伤穿戴者自己 —— 无论伤害来源是谁、是否存在实体来源；
        // 否则反伤给伤害来源实体（可为玩家自己：玩家自身让护甲受伤同样被反伤），
        // 来源不存在或不是 LivingEntity 时为 null（默认不反伤）
        LivingEntity defaultTarget;
        if (reflectToSelf) {
            defaultTarget = entity;
        } else if (source.getEntity() instanceof LivingEntity sourceEntity) {
            defaultTarget = sourceEntity;
        } else {
            defaultTarget = null;
        }
        // 派发荆棘反伤事件：监听者可改写反伤量 / 目标或取消；无监听者时行为与不派发完全一致。
        // 反伤伤害（thorns 类型）本身不累积反伤（上方 isReflectionDamage 分支），
        // 故反伤链路不会再次派发本事件，无需在监听者侧防环。
        ArmorReflectEvent reflectEvent = new ArmorReflectEvent(
                entity, source, reflections, reflectToSelf, reflectedDamage, defaultTarget);
        NeoForge.EVENT_BUS.post(reflectEvent);
        if (!reflectEvent.isCanceled() && reflectEvent.getAmount() > 0.0F && reflectEvent.getTarget() != null) {
            reflectEvent.getTarget().hurt(entity.damageSources().thorns(entity), reflectEvent.getAmount());
        }
        return true;
    }
}
