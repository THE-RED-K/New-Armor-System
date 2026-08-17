package dev.newarmorsystem.api;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * 护甲受损事件 —— 每件护甲的耐久真正被扣掉之前触发，可逐件改写扣血量，或整体取消本次损耗。
 *
 * <p>本事件由 {@code Inventory#hurtArmor(DamageSource, float, int[])} 的
 * {@code @Overwrite} 方法体在<b>采集完每件护甲的扣血量之后、真正施加损耗之前</b>派发
 * （见 {@code dev.newarmorsystem.mixin.InventoryMixin}）。没有被监听时行为与不派发完全一致：
 * {@link ArmorEntry#newDamage} 初值等于 {@link ArmorEntry#originalDamage}（即本模组公式
 * 算出的扣血量），照常截断为 {@code int} 后执行 {@code ItemStack#hurtAndBreak}。
 *
 * <p><b>取消语义</b>：{@link #setCanceled(boolean)} 后本模组直接结束 {@code hurtArmor}，
 * 本次伤害<b>不扣任何护甲耐久、也不产生反伤</b>（与 NeoForge / MesdagPortLib 的语义一致）。
 *
 * <p><b>与 NeoForge / MesdagPortLib 的对应关系</b>：本类与 NeoForge 1.21 的
 * {@code ArmorHurtEvent}（以及 MesdagPortLib 在 1.20.1 Forge 上的移植
 * {@code org.mesdag.portlib.event.entity.living.PortArmorHurtEvent}）字段与语义一一对应：
 * <ul>
 *   <li>{@link #getArmorMap()} 按 {@link EquipmentSlot} 索引 {@link ArmorEntry}；</li>
 *   <li>{@link #getArmorItemStack(EquipmentSlot)} / {@link #getOriginalDamage(EquipmentSlot)} /
 *       {@link #getNewDamage(EquipmentSlot)} / {@link #setNewDamage(EquipmentSlot, float)}
 *       —— 签名与 portlib 完全相同（含 {@code Float} 装箱返回、槽位不存在时返回 {@code null}）；</li>
 *   <li>{@link #getDamageSource()} 对应 portlib 的 {@code getDamageSource()}，
 *       实体由 {@link LivingEvent#getEntity()} 提供。</li>
 * </ul>
 *
 * <p>正因如此，附属模组可以<b>零成本转接</b>依赖 portlib 那套注入的模组：
 * 监听本事件 → 用 {@link #getArmorMap()} 组装 {@code PortArmorHurtEvent}
 * （每个 {@link ArmorEntry} 的 {@code armorItemStack} / {@code originalDamage} 直接填入）
 * → 经 {@code PortEventHandler.postEventWithReturn} 派发 → 若被取消则调用
 * {@link #setCanceled(boolean)}，否则把 portlib 事件里被改写过的
 * {@code newDamage} 写回本事件的 {@link ArmorEntry#newDamage}
 * （{@link #setNewDamage(EquipmentSlot, float)}）。这样那些模组的护甲受损钩子
 * 在本模组的 {@code @Overwrite} 公式下依然生效 —— 本模组不再需要为了兼容
 * 而放弃对 {@code hurtArmor} 的整体接管。
 *
 * @author THEREDK
 */
public class ArmorHurtEvent extends LivingEvent {

    /** 本次造成护甲损耗的伤害来源。 */
    private final DamageSource source;

    /** 本次参与损耗的护甲，按槽位索引（键集与派发时传入的一致）。 */
    private final EnumMap<EquipmentSlot, ArmorEntry> armorEntries;

    /**
     * @param armorEntries 本次参与损耗的护甲（按槽位索引，非空）
     * @param entity       护甲所属实体（玩家，{@link #getEntity()} 返回它）
     * @param source       伤害来源
     */
    public ArmorHurtEvent(EnumMap<EquipmentSlot, ArmorEntry> armorEntries, LivingEntity entity, DamageSource source) {
        super(entity);
        this.armorEntries = armorEntries;
        this.source = source;
    }

    /** 本事件可取消：取消后本次伤害不扣任何护甲耐久、也不产生反伤。 */
    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 某槽位护甲的物品堆。
     *
     * @param slot 护甲槽位
     * @return 该槽位的护甲堆；该槽位不在本次损耗范围内时返回 {@code null}
     */
    @Nullable
    public ItemStack getArmorItemStack(EquipmentSlot slot) {
        ArmorEntry entry = this.armorEntries.get(slot);
        return entry == null ? null : entry.armorItemStack;
    }

    /**
     * 某槽位护甲的<b>原始</b>扣血量（本模组公式 {@link NewCombatRules#getDurabilityLoss} 的结果）。
     *
     * @param slot 护甲槽位
     * @return 原始扣血量；该槽位不在本次损耗范围内时返回 {@code null}
     */
    @Nullable
    public Float getOriginalDamage(EquipmentSlot slot) {
        ArmorEntry entry = this.armorEntries.get(slot);
        return entry == null ? null : entry.originalDamage;
    }

    /**
     * 某槽位护甲<b>当前生效</b>的扣血量（监听者改写后的值，等于原值时说明未被改写）。
     *
     * @param slot 护甲槽位
     * @return 当前扣血量；该槽位不在本次损耗范围内时返回 {@code null}
     */
    @Nullable
    public Float getNewDamage(EquipmentSlot slot) {
        ArmorEntry entry = this.armorEntries.get(slot);
        return entry == null ? null : entry.newDamage;
    }

    /**
     * 改写某槽位护甲的扣血量。
     *
     * <p>实际施加时向下取整为 {@code int}（与不派发事件时一致），因此
     * {@code newDamage < 1} 表示该护甲本次不掉耐久；负数按 0 处理。
     *
     * @param slot      护甲槽位
     * @param newDamage 新的扣血量
     */
    public void setNewDamage(EquipmentSlot slot, float newDamage) {
        ArmorEntry entry = this.armorEntries.get(slot);
        if (entry != null) {
            entry.newDamage = newDamage;
        }
    }

    /**
     * 本次参与损耗的全部护甲（可写：修改 {@link ArmorEntry#newDamage} 等价于
     * {@link #setNewDamage(EquipmentSlot, float)}）。
     *
     * @return 按槽位索引的护甲表（活引用）
     */
    public Map<EquipmentSlot, ArmorEntry> getArmorMap() {
        return this.armorEntries;
    }

    /** @return 本次造成护甲损耗的伤害来源 */
    public DamageSource getDamageSource() {
        return this.source;
    }

    /**
     * 单件护甲的受损记录。
     *
     * <p>字段与 MesdagPortLib 的 {@code PortArmorHurtEvent.ArmorEntry} 完全一致
     * （{@code armorItemStack} / {@code originalDamage} / {@code newDamage}），
     * 便于附属模组逐字段转接。
     */
    public static class ArmorEntry {

        /** 该槽位护甲的物品堆（可被监听者替换）。 */
        public ItemStack armorItemStack;

        /** 原始扣血量，不可变。 */
        public final float originalDamage;

        /** 当前生效的扣血量，初值等于 {@link #originalDamage}。 */
        public float newDamage;

        /**
         * @param armorItemStack 护甲堆
         * @param originalDamage 原始扣血量（{@code newDamage} 初值同此）
         */
        public ArmorEntry(ItemStack armorItemStack, float originalDamage) {
            this.armorItemStack = armorItemStack;
            this.originalDamage = originalDamage;
            this.newDamage = originalDamage;
        }
    }
}
