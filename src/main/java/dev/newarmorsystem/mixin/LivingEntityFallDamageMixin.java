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
 * {@code Mth.ceil(double)} 调用，使玩家使用质量系统摔落公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率}
 * （{@link FallDamage}）；非玩家实体原样走原版公式。
 *
 * <p>注入点 {@code Mth.ceil(D)I} 是原版伤害的最终整形入口（经字节码核对，
 * {@code calculateFallDamage} 内唯一一处），重定向其返回值后，原版后续逻辑
 * （摔落音效、{@code hurt}、{@code Entity.causeFallDamage} 返回值）全部保留。
 * 该调用前原版已完成：
 * <ul>
 *   <li>{@code CommonHooks.onLivingFall}（{@code LivingFallEvent}，可被其它模组修改
 *       distance / damageMultiplier 或取消）；</li>
 *   <li>{@code FALL_DAMAGE_IMMUNE} 标签判定（免疫实体在更早处返回 0，
 *       不会到达本注入点）；</li>
 *   <li>1.21 新增的属性 {@code Attributes.SAFE_FALL_DISTANCE} 与
 *       {@code Attributes.FALL_DAMAGE_MULTIPLIER} 参与的原版表达式。</li>
 * </ul>
 * 因此事件对 distance 与倍率的修改均传入质量公式生效；跳跃提升对安全高度的
 * 乘算加成由 {@link FallDamage} 内部处理。
 *
 * <p><b>1.20.1 → 1.21.1</b>：目标描述符由 {@code Mth.ceil(F)I} 变为
 * {@code Mth.ceil(D)I}（1.21 的表达式为 double），handler 首参类型随之改为 {@code double}。
 *
 * @author THEREDK
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallDamageMixin {

    /**
     * Mixin 规定 {@code @Redirect} handler 参数顺序为：
     * {@code [被重定向调用的参数(实例调用还需接收者)] + [外层方法参数]}。
     * 此处被重定向的是静态调用 {@code Mth.ceil(double)}，故其参数 {@code pCeilValue}
     * 必须排在 {@code calculateFallDamage(float, float)} 的两个参数之前。
     *
     * @param pCeilValue  原版 ceil 表达式（含安全距离与伤害倍率属性）的值
     * @param pDistance   fallDistance（已含 {@code LivingFallEvent} 的修改）
     * @param pMultiplier 伤害倍率（模组可通过 {@code LivingFallEvent.setDamageMultiplier} 修改）
     */
    @Redirect(
            method = "calculateFallDamage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(D)I")
    )
    private int newArmorSystem$customFallDamage(double pCeilValue, float pDistance, float pMultiplier) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return Mth.ceil(pCeilValue);
        }
        return FallDamage.computeDamage(player, pDistance, pMultiplier);
    }
}
