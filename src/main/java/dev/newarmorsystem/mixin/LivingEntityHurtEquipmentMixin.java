package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.ArmorHurtHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 1.20.1 对 {@code Inventory#hurtArmor} 的公式接管迁移到 1.21.1 的新落点
 * {@link LivingEntity#doHurtEquipment(DamageSource, float, EquipmentSlot...)}。
 *
 * <p><b>为什么是这里</b>：1.21.1 起护甲耐久损耗不再由 {@code Inventory#hurtArmor} 承担
 * （该方法已不存在），而是由 {@code Player#hurtArmor} 等调用
 * {@code LivingEntity#doHurtEquipment}；且 NeoForge 已把实际施加逻辑搬进
 * {@code CommonHooks#onArmorHurt}（原方法体在其后直接 {@code return}）。
 * 更关键的是：{@code doHurtEquipment} 会先把原始伤害压成
 * {@code i = (int)max(1, damage / 4)} 再传给 {@code onArmorHurt}，
 * <b>模组公式所需的原始伤害在该点已经丢失</b>，因此接管点必须早于该计算 —— 即本方法头部。
 *
 * <p><b>取消语义</b>：仅当 {@link ArmorHurtHandler#apply} 返回 {@code true}
 * （确有护甲参与本次损耗，或已被事件取消）时取消原方法；无任何可受损护甲时
 * <b>不取消</b>，交回原版 / NeoForge 路径 —— 原生 {@code ArmorHurtEvent} 仍会照常派发
 * （空表），第三方模组依赖该事件的监听不会因本模组而失效。
 *
 * @author THEREDK
 * @reason 将原版与韧性无关的耐久损耗公式替换为模组自定义公式，并驱动反伤链路
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtEquipmentMixin {

    /**
     * @param damageSource 伤害来源（原参数）
     * @param damageAmount 损耗前的原始伤害（原参数，未经 {@code /4}）
     * @param slots        本次参与损耗的护甲槽位（原参数）
     * @param ci           取消回调
     */
    @Inject(method = "doHurtEquipment", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$hurtEquipment(DamageSource damageSource, float damageAmount,
                                              EquipmentSlot[] slots, CallbackInfo ci) {
        if (ArmorHurtHandler.apply((LivingEntity) (Object) this, damageSource, damageAmount, slots)) {
            ci.cancel();
        }
    }
}
