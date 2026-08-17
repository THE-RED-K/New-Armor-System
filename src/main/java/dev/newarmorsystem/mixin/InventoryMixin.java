package dev.newarmorsystem.mixin;


import java.util.EnumMap;
import java.util.Map;

import dev.newarmorsystem.api.ArmorHurtEvent;
import dev.newarmorsystem.api.ArmorReflectEvent;
import dev.newarmorsystem.api.DamageReflection;
import dev.newarmorsystem.api.MixinPriorities;
import dev.newarmorsystem.api.NewCombatRules;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 用新护甲耐久损耗公式替换原版 {@link Inventory#hurtArmor(DamageSource, float, int[])} 中的损耗计算。
 * <p>
 * 原版损耗：{@code pDamage /= 4.0F}（每件护甲扣 max(原始伤害 / 4, 1) 点耐久，与韧性无关）。
 * 本模组：每件护甲按自身提供的韧性（attribute modifier）代入
 * {@link NewCombatRules#getDurabilityLoss(float, float, boolean)}，韧性越高损耗越小。
 * <p>
 * 注意：本模组使用 {@code @Overwrite} 直接覆盖原方法体，与其它同样修改护甲耐久损耗公式、
 * 也使用 {@code @Overwrite} 的 Mod 无法共存（后写入者会被 Mixin 跳过）—— 这是本模组的设计定位。
 * 但对方的<b>注入</b>（{@code @Inject} / {@code @ModifyVariable} / MixinExtras 包装等）仍能正常
 * 落在本模组接管后的方法体上：本类用低于默认值的优先级先写入，使它们的注入点依旧可解析
 * （机制见 {@link MixinPriorities}）。
 * 需要与该类 Mod 共存时<b>不要改回 {@code @Inject}</b>（会破坏「公式唯一接管」的定位）：
 * 改由附属模组监听本方法体派发的 {@link ArmorHurtEvent}，把它转接到依赖方的事件上，
 * 让依赖方在本模组的公式之下继续生效 —— 用法见 {@link ArmorHurtEvent} 的类注释。
 * <p>
 * 不要在此处添加 {@code remap = false}，否则发布 jar（运行时方法名为 SRG 名）会失效。
 *
 * @author THEREDK
 * @reason 将原版与韧性无关的耐久损耗公式替换为模组自定义公式
 */
// 接管型 mixin：低优先级先写入方法体，使其它模组的注入仍能落在接管后的方法上（机制与取值见 MixinPriorities）
@Mixin(value = Inventory.class, priority = MixinPriorities.TAKEOVER)
public abstract class InventoryMixin {

    @Shadow
    private NonNullList<ItemStack> armor;

    @Shadow
    private Player player;

    /**
     * 原版实现：仅把「伤害 / 4 均摊给每件护甲」的损耗计算行，
     * 替换为调用 {@link NewCombatRules#getDurabilityLoss(float, float, boolean)}，
     * 韧性按每件护甲自身提供的值（attribute modifier）计算。
     * 若该护甲附有耐久附魔（Unbreaking），则不在此处取整，
     * 把原始浮点商传递下去，由 {@code ItemStack#hurt} 中的 {@code floor} 统一舍入，
     * 避免双重取整；其余逻辑与目标方法体保持一致。
     *
     * <p><b>反伤</b>：护甲实际损失耐久时（{@code actualLoss > 0}），按
     * 反伤比例 × 损失耐久度累积反伤，在循环结束后施加 {@code thorns} 类型反伤。
     * 反伤比例 = {@link DamageReflection#of} 的材料比例 + 荆棘附魔等级 × 60%
     * （每级 +60%，必定反伤、无概率判定；原版荆棘机制已由
     * {@link ThornsEnchantmentMixin} 禁用）。反伤目标：
     * 默认对伤害来源实体（{@code DamageSource#getEntity()}，可为玩家自己）；
     * 若受损失护甲中任一件开启了 {@link DamageReflection#reflectsToSelf}
     * （诅咒类护甲），则<b>必定反伤玩家自己</b>（穿戴者），而不是反伤目标。
     * 低伤害攻击让护甲不掉耐久 → 无反伤，因此反伤对小怪无效（刻意设计）。
     * {@code thorns} 反伤伤害不再触发反伤，防止反伤护甲互击时无限循环。
     *
     * <p><b>事件</b>：采集完每件护甲的原始扣血量后先派发 {@link ArmorHurtEvent}
     * （字段与语义同 NeoForge / MesdagPortLib 的护甲受损事件），监听者可按槽位改写
     * 扣血量或整体取消；累积反伤量施加之前另派发 {@link ArmorReflectEvent}
     * （监听者可改写反伤量 / 目标或取消）。<b>两事件均无监听者时行为与不派发完全一致</b>。
     *
     * @author THEREDK
     * @reason 原版损耗公式与韧性无关，模组公式使其与每件护甲自身韧性挂钩，并将舍入唯一化到 hurt
     */
    @Overwrite
    public void hurtArmor(DamageSource pSource, float pDamage, int[] pArmorPieces) {
        if (!(pDamage <= 0.0F)) {
            boolean isReflectionDamage = pSource.is(DamageTypes.THORNS);
            float reflectedDamage = 0.0F;
            // 每件护甲对反伤总量的贡献（比例 × 该件实际损耗），派发 ArmorReflectEvent 用
            EnumMap<EquipmentSlot, Float> reflections = new EnumMap<>(EquipmentSlot.class);
            // 1) 采集本次参与损耗的护甲（筛选条件与原实现一致），算出各自的原始扣血量
            EnumMap<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entries = new EnumMap<>(EquipmentSlot.class);
            for (int i : pArmorPieces) {
                ItemStack itemstack = this.armor.get(i);
                if ((!pSource.is(DamageTypeTags.IS_FIRE) || !itemstack.getItem().isFireResistant()) && itemstack.getItem() instanceof ArmorItem) {
                    EquipmentSlot slot = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, i);
                    boolean hasUnbreaking = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, itemstack) > 0;
                    float loss = NewCombatRules.getDurabilityLoss(pDamage,
                            NewCombatRules.getPieceToughness(itemstack, slot), hasUnbreaking);
                    entries.put(slot, new ArmorHurtEvent.ArmorEntry(itemstack, loss));
                }
            }
            // 2) 派发护甲受损事件：监听者可按槽位改写扣血量（ArmorHurtEvent#setNewDamage），
            //    取消则本次不扣任何护甲耐久、也不产生反伤。
            //    无监听者时 newDamage == originalDamage，行为与不派发事件完全一致。
            if (!entries.isEmpty() && MinecraftForge.EVENT_BUS.post(new ArmorHurtEvent(entries, this.player, pSource))) {
                return;
            }
            // 诅咒类护甲开关：任一受损失护甲开启"必定反伤穿戴者自己"即为 true
            boolean reflectToSelf = false;
            for (Map.Entry<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entry : entries.entrySet()) {
                EquipmentSlot slot = entry.getKey();
                ArmorHurtEvent.ArmorEntry armorEntry = entry.getValue();
                // 扣血量取事件改写后的值并向下取整（与不派发事件时一致）
                int actualLoss = (int) armorEntry.newDamage;
                if (actualLoss > 0) {
                    ItemStack itemstack = armorEntry.armorItemStack;
                    if (itemstack.getItem() instanceof ArmorItem armorItem) {
                        // 反伤 = 反伤比例 × 护甲损失的耐久度（荆棘反伤不再反伤，防止无限循环）
                        if (!isReflectionDamage) {
                            if (DamageReflection.reflectsToSelf(armorItem.getMaterial())) {
                                reflectToSelf = true;
                            }
                            // 反伤比例 = 材料反伤比例 + 荆棘附魔每级 +60%（必定反伤，无概率）
                            double ratio = DamageReflection.of(armorItem.getMaterial());
                            int thornsLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.THORNS, itemstack);
                            if (thornsLevel > 0) {
                                ratio += 0.6 * thornsLevel;
                            }
                            float contribution = (float) (ratio * actualLoss);
                            reflections.put(slot, contribution);
                            reflectedDamage += contribution;
                        }
                        itemstack.hurtAndBreak(actualLoss, this.player, (p_35997_) -> {
                            p_35997_.broadcastBreakEvent(slot);
                        });
                    }
                }
            }
            // 护甲损失耐久后施加累积反伤（低伤害不掉耐久 → 无反伤，对小怪无效是刻意设计）
            if (reflectedDamage > 0.0F) {
                // 默认反伤目标：诅咒类护甲必定反伤玩家自己（穿戴者）—— 无论伤害来源是谁、是否存在实体来源；
                // 否则反伤给伤害来源实体（可为玩家自己：玩家自身让护甲受伤同样被反伤），
                // 来源不存在或不是 LivingEntity 时为 null（默认不反伤）
                LivingEntity defaultTarget;
                if (reflectToSelf) {
                    defaultTarget = this.player;
                } else if (pSource.getEntity() instanceof LivingEntity source) {
                    defaultTarget = source;
                } else {
                    defaultTarget = null;
                }
                // 派发荆棘反伤事件：监听者可改写反伤量 / 目标或取消；无监听者时行为与不派发完全一致。
                // 反伤伤害（thorns 类型）本身不累积反伤（上方 isReflectionDamage 分支），
                // 故反伤链路不会再次派发本事件，无需在监听者侧防环。
                ArmorReflectEvent event = new ArmorReflectEvent(this.player, pSource, reflections,
                        reflectToSelf, reflectedDamage, defaultTarget);
                if (!MinecraftForge.EVENT_BUS.post(event) && event.getAmount() > 0.0F && event.getTarget() != null) {
                    event.getTarget().hurt(this.player.damageSources().thorns(this.player), event.getAmount());
                }
            }
        }
    }
}
