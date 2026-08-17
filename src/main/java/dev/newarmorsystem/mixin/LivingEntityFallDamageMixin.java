package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.FallDamage;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 摔落伤害重定向（玩家专用）：重定向 {@code LivingEntity.calculateFallDamage} 中的
 * {@code Mth.ceil(float)} 调用，使玩家使用质量系统摔落公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率}
 * （{@link FallDamage}）；非玩家实体原样走原版公式。
 *
 * <p>注入点 {@code Mth.ceil(F)I} 是原版伤害的最终整形入口，重定向其返回值后，
 * 原版后续逻辑（摔落音效、{@code hurt}、{@code Entity.causeFallDamage} 返回值）
 * 全部保留。该调用前原版已完成 {@code ForgeHooks.onLivingFall}
 * （{@code LivingFallEvent}，可被其它模组修改 distance/damageMultiplier 或取消），
 * 因此事件对 distance 与倍率的修改均传入质量公式生效；跳跃提升对安全高度的
 * 乘算加成由 {@link FallDamage} 内部处理。
 *
 * @author THEREDK
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallDamageMixin {

    /**
     * Mixin 规定 {@code @Redirect} handler 参数顺序为：
     * {@code [被重定向调用的参数(静态调用直接是调用参数；实例调用含接收者)] + [外层方法参数]}，
     * 此处被重定向调用 {@code Mth.ceil(float)} 的参数是 pCeilValue，故其必须排在
     * {@code calculateFallDamage(float, float)} 的两个参数之前。
     *
     * @param pCeilValue  原版 ceil 表达式 {@code (fallDistance − 3 − 跳跃提升) × 倍率} 的值
     * @param pDistance   fallDistance（已含 {@code LivingFallEvent} 的修改）
     * @param pMultiplier 伤害倍率（模组可通过 {@code LivingFallEvent.setDamageMultiplier} 修改）
     */
    @Redirect(
            method = "calculateFallDamage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(F)I")
    )
    private int newArmorSystem$customFallDamage(float pCeilValue, float pDistance, float pMultiplier) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return Mth.ceil(pCeilValue);
        }
        return FallDamage.computeDamage(player, pDistance, pMultiplier);
    }
}
