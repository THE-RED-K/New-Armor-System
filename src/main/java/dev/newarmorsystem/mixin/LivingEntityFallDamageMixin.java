package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.FallDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 摔落伤害接管（玩家专用）：让玩家使用质量系统摔落公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 1920 × 倍率}
 * （{@link FallDamage}）；非玩家实体原样走原版公式。
 *
 * <p><b>为什么需要两处注入</b> —— 原版把摔落伤害<b>整形为 int</b> 之后才使用：
 * {@code causeFallDamage} 内先 {@code int i = calculateFallDamage(...)}，
 * 再 {@code if (i > 0) { 播报摔落音效; hurt(source, (float) i); }}。
 * 半格值（如 3.5）无法穿过这个 int 中转，故拆成两步：
 * <ol>
 *   <li>第一处 {@link ModifyArg} 只改 {@code calculateFallDamage} 内 {@code Mth.ceil(D)I}
 *       的<b>实参</b>（取整仍由原版执行）：决定<b>整数闸门</b>
 *       （{@code i > 0} 才进入"音效 + hurt"分支）与摔落音效强度。
 *       此处用 {@link FallDamage#computeRawDamage}（<b>不派发事件</b>，返回未取整的半格值）。</li>
 *   <li>第二处 {@link ModifyArg} 只改 {@code causeFallDamage} 内
 *       {@code hurt(source, (float) i)} 的<b>伤害实参</b>：把<b>半格精确值</b>交给 {@code hurt} ——
 *       它是原版管线里唯一以 {@code float} 接收摔落伤害的入口。此处用
 *       {@link FallDamage#computeDamage}（派发
 *       {@link dev.newarmorsystem.api.PlayerFallDamageEvent}，并取事件改写后的值）；
 *       非玩家/开关关闭时返回原值，行为与不注入完全一致（且不再二次调用 {@code hurt}）。</li>
 * </ol>
 *
 * <p><b>两处为什么都用 {@link ModifyArg} 而不是 {@link Redirect}</b>：{@code @Redirect} 会把
 * 目标调用指令<b>整体替换</b>成自己的 handler 调用，该指令从此不再是原版形状，
 * 其它模组针对它的注入随即失败 —— 本模组在一次真实崩溃里已踩实：{@code portlib} 用
 * {@code @ModifyArg} 给 {@code hurt} 的伤害参数乘上摔落伤害倍率属性，被 {@code @Redirect}
 * 顶掉后抛 {@code InvalidInjectionException}，<b>启动即崩</b>。
 * {@code @ModifyArg} <b>只改那一个参数</b>、调用本身保持原版，故能与其它模组的 {@code @ModifyArg}
 * 串联共存（{@code @ModifyArg} 逐条叠加，不像 {@code @Redirect} 那样独占指令）。
 * <b>本类因此不再替换任何调用指令</b>：两处都是"只换实参、原版指令照跑"，
 * {@code Mth.ceil} 与 {@code hurt} 都保持原版形状，其它模组的注入用哪种注解都还能命中。
 *
 * <p><b>宿主形参怎么拿：两处 {@code @Inject(HEAD)} 暂存</b>（<b>实测教训，勿再改回捕获</b>）——
 * {@code @ModifyArg} 的 handler <b>不能</b>捕获宿主（外层）方法的形参：Mixin 只按
 * <b>被注入的那条调用</b>的实参校验 handler 形参表（{@code Mth.ceil(D)} 只能提供 {@code (D)}），
 * 多声明即报
 * {@code InvalidInjectionException: ... targets a method with an invalid signature (D), expected (DFF)}，
 * <b>启动即崩</b>（实测于 1.21.1 / Mixin 0.8.7）。
 * 注意 {@code @Redirect} <b>反而</b>支持捕获外层形参 —— 本模组旧实现正是靠它工作，
 * 也是当初把该规则误推到 {@code @ModifyArg} 上的来源。
 * 因此改为：两个宿主方法各自在 {@code @At("HEAD")} 用 {@code @Inject} 把形参暂存进
 * {@link Unique} 字段（字段被并入 {@code LivingEntity}，天然按实体实例隔离；每次调用都覆写，
 * 不会读到陈旧值），{@code @ModifyArg} 只接收被改实参、从字段取值。
 * 两处使用<b>独立</b>字段对 —— {@code calculateFallDamage} 的暂存不会覆盖
 * {@code causeFallDamage} 的值（二者的形参可能被不同模组分别改动）。
 * 另一条同样实测出来的规则：宿主返回非 {@code void} 时，{@code @Inject} 的末尾回调
 * <b>必须</b>是 {@code CallbackInfoReturnable<包装类型>} —— {@code causeFallDamage} 返回
 * {@code boolean}（{@code (FFL…DamageSource;)Z}），故用 {@code CallbackInfoReturnable<Boolean>}；
 * 写普通的 {@code CallbackInfo} 会被拒并启动崩溃。
 *
 * <p><b>与属性倍率的配合</b>：本类给出的是<b>绝对值</b>（不是把原值按比例缩放），
 * 因此同参数的其它乘法会被覆盖 —— 摔落伤害倍率属性<b>由本模组自己读</b>：
 * 1.21.1 读原版 {@code Attributes.FALL_DAMAGE_MULTIPLIER}（见 {@link FallDamage#compute}），
 * 1.20.1 则由该分支自行注册等价属性（{@code ModAttributes}），并与移植库（{@code portlib} 等）
 * 注册的同类属性在公式里相乘（其 mixin 需禁用，属性注册不受影响，应用唯一由本模组完成；
 * 1.21.1 本分支无需该兼容层，因为两个属性都是原版属性）。
 *
 * <p><b>注入点前原版已完成</b>：
 * <ul>
 *   <li>{@code CommonHooks.onLivingFall}（{@code LivingFallEvent}，可被其它模组修改
 *       distance / damageMultiplier 或取消）；</li>
 *   <li>{@code FALL_DAMAGE_IMMUNE} 标签判定（免疫实体在更早处返回 0，
 *       不会到达本注入点）；</li>
 *   <li>1.21 新增的属性 {@code Attributes.SAFE_FALL_DISTANCE} 与
 *       {@code Attributes.FALL_DAMAGE_MULTIPLIER} 参与的原版表达式。</li>
 * </ul>
 * 因此事件对 distance 与倍率的修改均传入质量公式生效；跳跃提升对安全高度的
 * 乘算加成由 {@link FallDamage} 内部处理（见 {@code JumpBoostSafeFallDistanceMixin}）。
 *
 * <p><b>事件时机</b>：{@code PlayerFallDamageEvent} 现在派发在<b>摔落音效之后、
 * {@code hurt} 之前</b>（原版先播音效再结算伤害）。监听者设 0 仍能免除全部伤害，
 * 但音效已播出；安全高度以内的坠落不会进入原版 {@code i > 0} 分支，故<b>不派发</b>事件。
 *
 * <p><b>功能总开关</b>：{@code feature_toggles.fallDamageFormulaEnabled} 关闭时两处注入
 * 都原样放行（各自返回原实参，原版指令照跑），即完全原版摔落伤害；同时
 * {@code PlayerMassEffects} 会把重力与安全高度覆写回归原版口径
 * （见其 {@code syncGravity} / {@code syncSafeFallDistance}）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：目标描述符由 {@code Mth.ceil(F)I} 变为 {@code Mth.ceil(D)I}
 * （1.21 的表达式为 double），handler 首参类型随之改为 {@code double}；
 * {@code causeFallDamage} 的 {@code hurt} 调用形状两版一致。
 *
 * @author THEREDK
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallDamageMixin {

    /** {@code calculateFallDamage} 的宿主形参暂存（<b>仅</b>供第一处 {@code @ModifyArg} 读取）。 */
    @Unique
    private float newArmorSystem$gateDistance;
    @Unique
    private float newArmorSystem$gateMultiplier;

    /** {@code causeFallDamage} 的宿主形参暂存（<b>仅</b>供第二处 {@code @ModifyArg} 读取）。 */
    @Unique
    private float newArmorSystem$hurtDistance;
    @Unique
    private float newArmorSystem$hurtMultiplier;

    /**
     * 暂存 {@code calculateFallDamage(float, float)} 的两个形参，供 {@code Mth.ceil} 处的
     * {@code @ModifyArg} 使用（原因见类 javadoc：{@code @ModifyArg} 拿不到宿主形参）。
     *
     * @param pFallDistance 宿主形参 1：fallDistance（已含 {@code LivingFallEvent} 的修改）
     * @param pMultiplier   宿主形参 2：伤害倍率（模组可经 {@code LivingFallEvent} 修改）
     * @param cir           返回型回调；本注入只暂存，不触碰返回值
     */
    @Inject(method = "calculateFallDamage", at = @At("HEAD"))
    private void newArmorSystem$captureCalculateFallDamage(float pFallDistance, float pMultiplier,
                                                           CallbackInfoReturnable<Integer> cir) {
        newArmorSystem$gateDistance = pFallDistance;
        newArmorSystem$gateMultiplier = pMultiplier;
    }

    /**
     * 暂存 {@code causeFallDamage(float, float, DamageSource)} 的前两个形参，供 {@code hurt} 处的
     * {@code @ModifyArg} 使用。
     *
     * <p>{@code DamageSource} 本注入不需要，但仍完整声明 —— 形参表按宿主声明顺序取<b>前缀</b>，
     * 这里刻意使用"全量形参"写法，避免在形参表规则上再踩一次坑。
     *
     * @param pFallDistance 宿主形参 1：fallDistance（已含 {@code LivingFallEvent} 的修改）
     * @param pMultiplier   宿主形参 2：伤害倍率（模组可经 {@code LivingFallEvent} 修改）
     * @param pSource       宿主形参 3：伤害来源（未使用，仅为形参表完整）
     * @param cir           返回型回调 —— 宿主返回 {@code boolean}，故 Mixin <b>要求</b>末尾形参是
     *                      {@code CallbackInfoReturnable<Boolean>}；写普通的 {@code CallbackInfo}
     *                      会报 {@code Invalid descriptor ... CallbackInfoReturnable is required!}
     *                      并启动崩溃。本注入只暂存，不触碰返回值。
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void newArmorSystem$captureCauseFallDamage(float pFallDistance, float pMultiplier,
                                                       DamageSource pSource, CallbackInfoReturnable<Boolean> cir) {
        newArmorSystem$hurtDistance = pFallDistance;
        newArmorSystem$hurtMultiplier = pMultiplier;
    }

    /**
     * 接管 {@code Mth.ceil} 的<b>实参</b>（取整仍由原版 {@code Mth.ceil} 自己完成）。
     *
     * <p>{@code @ModifyArg(index = 0)}：handler <b>只</b>声明被修改的实参（{@code Mth.ceil} 的
     * double 实参，含安全距离与伤害倍率属性的原版表达式）；宿主
     * {@code calculateFallDamage(float, float)} 的形参由
     * {@link #newArmorSystem$captureCalculateFallDamage} 暂存（原因见类 javadoc）。
     *
     * <p><b>与改造前逐位一致</b>：改造前本方法自己 {@code Mth.ceil(...)} 后返回 {@code int}
     * 替换整个调用；现在返回<b>未取整</b>的值，由原版紧接着执行 {@code Mth.ceil} ——
     * 取整只发生一次、参数完全相同，故整数闸门 {@code i} 与摔落音效强度都不变。
     *
     * @param pCeilValue 原版 ceil 表达式（含安全距离与伤害倍率属性）的值
     * @return 交给 {@code Mth.ceil} 的值：本模组公式的<b>原始（半格）值</b>，取整由原版完成
     */
    @ModifyArg(
            method = "calculateFallDamage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(D)I"),
            index = 0
    )
    private double newArmorSystem$fallDamageGate(double pCeilValue) {
        // 功能总开关关闭：返回原值（等价"原样放行"，原版自己 ceil，即原版摔落伤害）
        if (Config.COMMON_SPEC.isLoaded() && !Config.COMMON.fallDamageFormulaEnabled.get()) {
            return pCeilValue;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return pCeilValue;
        }
        // 闸门只要"是否大于 0"，与 computeDamage 共用同一份计算故数值必然一致
        return FallDamage.computeRawDamage(player, newArmorSystem$gateDistance, newArmorSystem$gateMultiplier);
    }

    /**
     * 接管 {@code hurt} 的<b>伤害参数</b>（只改参数，不替换调用）。
     *
     * <p>handler <b>只</b>声明被修改的实参；宿主
     * {@code causeFallDamage(float fallDistance, float multiplier, DamageSource source)}
     * 的距离与倍率由 {@link #newArmorSystem$captureCauseFallDamage} 暂存（原因见类 javadoc）。
     *
     * @param pAmount 被修改的实参：原版 int 伤害 {@code (float) i}
     * @return 交给 {@code hurt} 的伤害：本模组公式值；非本模组路径返回 {@code pAmount} 原值
     */
    @ModifyArg(
            method = "causeFallDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            index = 1
    )
    private float newArmorSystem$customFallDamage(float pAmount) {
        // 功能总开关关闭 / 非玩家：返回原值（等价"原样放行"，且不重新调用 hurt）
        if ((Config.COMMON_SPEC.isLoaded() && !Config.COMMON.fallDamageFormulaEnabled.get())
                || !((Object) this instanceof Player player)) {
            return pAmount;
        }
        // 事件被监听者改写时取改写值；无监听者时为半格取整值
        return (float) FallDamage.computeDamage(player, newArmorSystem$hurtDistance, newArmorSystem$hurtMultiplier);
    }
}
