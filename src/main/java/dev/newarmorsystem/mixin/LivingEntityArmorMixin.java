package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.MixinPriorities;
import dev.newarmorsystem.api.NewCombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;

/**
 * 用新护甲耐久损耗公式替换原版的护甲损耗计算。
 *
 * <p><b>1.20.1 → 1.21.1 的位置迁移</b>：1.20.1 的接管点是
 * {@code Inventory#hurtArmor(DamageSource, float, int[])}（{@code @Overwrite}）；
 * 1.21.1 该逻辑已迁移到 {@code LivingEntity#doHurtEquipment(DamageSource, float, EquipmentSlot...)}，
 * 并由 NeoForge 改写为调用 {@code CommonHooks#onArmorHurt} 派发<b>原生</b>
 * {@link ArmorHurtEvent}（原版循环被 Neo 的 {@code if (true) return;} 跳过）。故本模组的接管点
 * 相应迁移到 {@code doHurtEquipment} 的 HEAD。
 *
 * <p><b>公式</b>：原版损耗为 {@code max(1, floor(D/4))}，与韧性无关；
 * 本模组按每件护甲自身提供的韧性代入 {@link NewCombatRules#getDurabilityLoss}，韧性越高损耗越小。
 *
 * <p><b>事件</b>：不再自行定义护甲受损事件 —— NeoForge 1.21.1 的原生
 * {@link ArmorHurtEvent} 字段与语义同 1.20.1 版模组事件（{@code getArmorMap()} /
 * {@code getArmorItemStack} / {@code getOriginalDamage} / {@code getNewDamage} /
 * {@code setNewDamage} / {@code getDamageSource()}），附属模组直接监听原生事件即可；
 * 本类派发原生事件并沿用它「取消则本次不扣任何护甲耐久」的语义。
 *
 * <p>不要在此处添加 {@code remap = false}（NeoForge 1.20.5+ 已改用官方 Mojang 映射，
 * 开发期与运行期方法名一致）。
 *
 * @author THEREDK
 * @reason 将原版与韧性无关的耐久损耗公式替换为模组自定义公式
 */
@Mixin(value = LivingEntity.class, priority = MixinPriorities.TAKEOVER)
public abstract class LivingEntityArmorMixin {

    /**
     * 接管护甲耐久损耗：按每件护甲自身韧性套用模组公式，派发 NeoForge 原生
     * {@link ArmorHurtEvent}，再按事件结果施加损耗。
     *
     * <p>护甲筛选条件与 NeoForge {@code CommonHooks#onArmorHurt} 完全一致：
     * 非空 → {@link ArmorItem} → {@code canBeHurtBy(source)}（内含抗火判定）；
     * 不满足者原始损耗记为 0（保留在事件 map 中，行为与原生一致）。
     *
     * @author THEREDK
     * @reason 原版损耗公式与韧性无关，模组公式使其与每件护甲自身韧性挂钩
     */
    @Inject(method = "doHurtEquipment", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$hurtArmorWithToughness(DamageSource source, float damageAmount,
                                                       EquipmentSlot[] slots, CallbackInfo ci) {
        if (damageAmount <= 0.0F) {
            return;  // 与原实现一致：非正伤害直接无事发生
        }
        LivingEntity self = (LivingEntity) (Object) this;
        EnumMap<EquipmentSlot, ArmorHurtEvent.ArmorEntry> armorMap = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : slots) {
            ItemStack stack = self.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            boolean hurtable = stack.getItem() instanceof ArmorItem && stack.canBeHurtBy(source);
            float loss = hurtable
                    ? NewCombatRules.getDurabilityLoss(damageAmount,
                    NewCombatRules.getPieceToughness(stack, slot),
                    NewCombatRules.hasUnbreaking(stack))
                    : 0.0F;
            armorMap.put(slot, new ArmorHurtEvent.ArmorEntry(stack, loss));
        }

        ArmorHurtEvent event = NeoForge.EVENT_BUS.post(new ArmorHurtEvent(armorMap, self, source));
        if (!event.isCanceled()) {
            // 与原生一致：施加损耗时把 newDamage 截断为 int（唯一舍入点）
            event.getArmorMap().forEach((slot, entry) ->
                    entry.armorItemStack.hurtAndBreak((int) entry.newDamage, self, slot));
        }
        ci.cancel();  // 已完整接管，跳过 NeoForge 的原生 onArmorHurt 调用
    }
}
