package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.DamageReflection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.ThornsEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 重做荆棘附魔（Thorns）：移除原版机制，并入本模组反伤系统。
 *
 * <p>原版 {@code ThornsEnchantment.doPostHurt}：按概率（{@code 0.15 × 等级}）触发反伤
 * （{@code 1 ~ 4} 点伤害），且触发时对随机一件荆棘护甲<b>额外损耗 2 点耐久</b>。
 *
 * <p>本模组：反伤在护甲<b>损失耐久时</b>统一结算
 * （{@code InventoryMixin#hurtArmor}），反伤比例 = 材料反伤比例 + 荆棘等级 × 系数
 * （{@code damage_reflection.thornsCoefficient}，默认 0.15、可配置、不封顶），
 * <b>必定反伤</b>（无概率判定）。故本模组反伤系统开启时，原版机制整体停用。
 *
 * <p><b>功能总开关</b>：{@code feature_toggles.damageReflectionEnabled} 关闭时
 * <b>不停用</b>原版荆棘 —— 由 {@link DamageReflection#isEnabled()} 判定，实现「关闭即完全原版」。
 *
 * <p><b>为什么用 {@code @Inject} 而非 {@code @Overwrite}</b>：需要按开关在
 * 「停用原版」与「保持原版」之间切换，覆写无法表达「保持原版」；而且 {@code @Inject} + cancel
 * 同样能达成整体停用，却不必复刻原版方法体、也不占用 {@code MixinPriorities.TAKEOVER}
 * （该常量专供整体接管方法体的 {@code @Overwrite}）。行为与开关无关时二者完全等价。
 *
 * <p>不要在此处添加 {@code remap = false}。
 *
 * @author THEREDK
 * @reason 原版荆棘概率反伤与额外耐久损耗并入模组反伤系统（开关关闭时保持原版）
 */
@Mixin(ThornsEnchantment.class)
public abstract class ThornsEnchantmentMixin {

    /**
     * 反伤系统开启时，整体停用原版荆棘（概率反伤 + 额外耐久损耗）。
     *
     * @author THEREDK
     * @reason 原版荆棘机制并入模组反伤系统
     */
    @Inject(method = "doPostHurt", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$disableVanillaThorns(LivingEntity pUser, Entity pTarget, int pLevel,
                                                     CallbackInfo ci) {
        if (DamageReflection.isEnabled()) {
            ci.cancel();
        }
    }
}
