package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.FallDamage;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 摔落伤害接管（玩家专用）：让玩家使用质量系统摔落公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率}
 * （{@link FallDamage}）；非玩家实体原样走原版公式。
 *
 * <p><b>为什么需要两处注入</b> —— 原版把摔落伤害<b>整形为 int</b> 之后才使用：
 * {@code causeFallDamage} 内先 {@code int i = calculateFallDamage(...)}，
 * 再 {@code if (i > 0) { 播报摔落音效; hurt(pSource, (float) i); }}。
 * 半格值（如 3.5）无法穿过这个 int 中转，故拆成两步：
 * <ol>
 *   <li>第一处 {@link Redirect} 改写 {@code calculateFallDamage} 内的 {@code Mth.ceil(F)I}：
 *       决定<b>整数闸门</b>（{@code i > 0} 才进入"音效 + hurt"分支）与摔落音效强度。
 *       此处用 {@link FallDamage#computeRawDamage}（<b>不派发事件</b>）。</li>
 *   <li>第二处 {@link Redirect} 接管 {@code causeFallDamage} 内的
 *       {@code hurt(pSource, (float) i)} 调用：把<b>半格精确值</b>交给 {@code hurt} ——
 *       它是原版管线里唯一以 {@code float} 接收摔落伤害的入口。此处用
 *       {@link FallDamage#computeDamage}（派发
 *       {@link dev.newarmorsystem.api.PlayerFallDamageEvent}，并取事件改写后的值）；
 *       非玩家/开关关闭时在 handler 内原样转调 {@code hurt}，行为与不注入完全一致。</li>
 * </ol>
 *
 * <p><b>两处为什么都用 {@link Redirect}</b>：{@code @ModifyArg} 的 handler
 * <b>不接受</b>外层方法参数捕获（本仓库也从未用过该注解），而 {@code @Redirect} 的
 * 「{@code [接收者][被调用参数] + [外层方法参数]}」签名在本模组已有两处稳定实践
 * （本类第一处注入与 {@code JumpBoostSafeFallDistanceMixin}）。
 * 代价仅是 handler 需自行转调一次 {@code hurt}（转调的就是原调用本身，语义不变）。
 *
 * <p><b>注入点前原版已完成</b> {@code ForgeHooks.onLivingFall}
 * （{@code LivingFallEvent}，可被其它模组修改 distance/damageMultiplier 或取消），
 * 因此事件对 distance 与倍率的修改均传入质量公式生效；跳跃提升对安全高度的
 * 乘算加成由 {@link FallDamage} 内部处理。
 *
 * <p><b>事件时机</b>：{@code PlayerFallDamageEvent} 现在派发在<b>摔落音效之后、
 * {@code hurt} 之前</b>（原版先播音效再结算伤害）。监听者设 0 仍能免除全部伤害，
 * 但音效已播出；安全高度以内的坠落不会进入原版 {@code i > 0} 分支，故<b>不派发</b>事件。
 *
 * <p><b>功能总开关</b>：{@code feature_toggles.fallDamageFormulaEnabled} 关闭时两处注入
 * 都原样放行（{@code Mth.ceil} 原值 / 原伤害转调），即完全原版摔落伤害；同时
 * {@code PlayerMassEffects} 会把重力与安全高度覆写回归原版口径。
 *
 * <p><b>1.21.1 → 1.20.1</b>：目标描述符由 {@code Mth.ceil(D)I} 变为 {@code Mth.ceil(F)I}
 * （1.20.1 的表达式为 float），handler 首参类型随之改为 {@code float}；
 * {@code causeFallDamage} 的 {@code hurt} 调用形状两版一致。
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
     * @return 整数闸门值：本模组公式结果向上取整（&gt; 0 才进入原版"音效 + hurt"分支）
     */
    @Redirect(
            method = "calculateFallDamage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(F)I")
    )
    private int newArmorSystem$fallDamageGate(float pCeilValue, float pDistance, float pMultiplier) {
        // 功能总开关关闭：原样返回原版 ceil 结果，即原版摔落伤害
        if (!FallDamage.isFormulaEnabled()) {
            return Mth.ceil(pCeilValue);
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return Mth.ceil(pCeilValue);
        }
        // 闸门只要"是否大于 0"，与 computeDamage 共用同一份计算故数值必然一致
        return Mth.ceil(FallDamage.computeRawDamage(player, pDistance, pMultiplier));
    }

    /**
     * 接管 {@code hurt} 调用，把原版 int 伤害换成半格精确值。
     *
     * <p>Mixin 规定 {@code @Redirect} handler 参数顺序为
     * {@code [接收者] + [被重定向调用的参数] + [外层方法参数]}（与
     * {@code JumpBoostSafeFallDistanceMixin} 的实例调用重定向同构）。此处被重定向的是
     * {@code this.hurt(DamageSource, float)}：接收者即本实体，其后是调用参数
     * （伤害来源、原版 int 伤害），最后是外层方法
     * {@code causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource)}
     * 的三个参数。
     *
     * @param pSelf       接收者：被调用 {@code hurt} 的实体（即 {@code this}）
     * @param pSource     调用参数：摔落伤害来源
     * @param pAmount     调用参数：原版 int 伤害 {@code (float) i}
     * @param pDistance   外层参数：fallDistance（已含 {@code LivingFallEvent} 的修改）
     * @param pMultiplier 外层参数：伤害倍率
     * @return {@code hurt} 的返回值；非本模组路径与原版完全一致
     */
    @Redirect(
            method = "causeFallDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean newArmorSystem$customFallDamage(LivingEntity pSelf, DamageSource pSource, float pAmount,
                                                    float pDistance, float pMultiplier) {
        // 功能总开关关闭：原样转调
        if (!FallDamage.isFormulaEnabled() || !(pSelf instanceof Player player)) {
            return pSelf.hurt(pSource, pAmount);
        }
        // 事件被监听者改写时取改写值；无监听者时为半格取整值
        return pSelf.hurt(pSource, (float) FallDamage.computeDamage(player, pDistance, pMultiplier));
    }
}
