package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 跳跃提升对安全下落距离的加成由原版的 <b>+1 格/级（线性加算）</b>
 * 改为 <b>乘算</b>：{@code 安全高度 = 基础安全高度 × (1 + fallJumpSafeGrowth × 等级)}。
 *
 * <p><b>1.21 的属性化改造</b>：1.20.1 中安全高度是 {@code calculateFallDamage} 里的硬编码
 * {@code fallDistance - 3.0F - 跳跃等级}；1.21 起改为原版属性
 * {@link Attributes#SAFE_FALL_DISTANCE}（默认 3.0），而跳跃提升的效果注册为它的
 * 属性修饰符（见 {@code MobEffects.JUMP}）：
 *
 * <pre>{@code
 * .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE,
 *         ResourceLocation.withDefaultNamespace("effect.jump_boost"),
 *         1.0, AttributeModifier.Operation.ADD_VALUE)   // ← 每级 +1.0（线性）
 * }</pre>
 *
 * <p><b>为什么不改注册处、而改施加处</b>：{@code MobEffects} 的静态初始化发生在
 * Bootstrap 阶段，早于模组配置加载，那时读 {@link Config} 会抛
 * "Cannot get config value before config is loaded"（与 {@code RangedAttributeMixin}
 * 对 {@code Attributes} 静态初始化的处理同理）。而
 * {@link MobEffect#addAttributeModifiers(AttributeMap, int)} 在<b>效果生效时</b>才被调用，
 * 彼时配置必然已加载，因此接管点选在它的
 * {@code AttributeInstance#addPermanentModifier} 调用处。
 *
 * <p><b>等价性</b>：原版为 {@code 基础值 + 1.0 × (amplifier+1)}；本模组改为写入
 * {@code ADD_MULTIPLIED_BASE} 且 {@code amount = fallJumpSafeGrowth × (amplifier+1)}，
 * 即 {@code 基础值 + 基础值 × fallJumpSafeGrowth × 等级} —— 与 1.20.1 模组公式
 * {@code 安全高度 × (1 + growth × 等级)} 完全一致（默认 growth=0.2：1 级 ≈ 3.6、2 级 ≈ 4.2）。
 * 因为乘的是属性<b>基础值</b>，其它模组对基础值的修改会被自然带入。
 *
 * <p><b>移除链路</b>：{@code MobEffect#removeAttributeModifiers} 按修饰符 id 移除，
 * 本模组写入的修饰符沿用原版 id（{@code minecraft:effect.jump_boost}），故效果结束时能正常清除。
 *
 * <p>只影响 id 为 {@code minecraft:effect.jump_boost} 且属性为
 * {@link Attributes#SAFE_FALL_DISTANCE} 的那一条修饰符，其余药水效果/属性原样放行。
 *
 * @author THEREDK
 * @reason 跳跃提升改为乘算提升安全下落距离
 */
@Mixin(MobEffect.class)
public abstract class JumpBoostSafeFallDistanceMixin {

    /** 原版跳跃提升写入安全下落距离所用的修饰符 id。 */
    private static final ResourceLocation NEW_ARMOR_SYSTEM$JUMP_BOOST_SAFE_FALL_ID =
            ResourceLocation.withDefaultNamespace("effect.jump_boost");

    /**
     * {@code @Redirect} handler 参数顺序：{@code [接收者][被重定向调用的参数] + [外层方法参数]}。
     * 此处被重定向的是实例方法 {@code AttributeInstance#addPermanentModifier(AttributeModifier)}，
     * 外层方法是 {@code addAttributeModifiers(AttributeMap, int)}。
     *
     * @param instance      被写入的属性实例（接收者）
     * @param modifier      原版要写入的修饰符（跳跃提升：+1.0/级 ADD_VALUE）
     * @param attributeMap  外层参数：目标实体的属性表
     * @param amplifier     外层参数：效果等级（原版按 {@code amount × (amplifier+1)} 缩放）
     */
    @Redirect(
            method = "addAttributeModifiers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;addPermanentModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V"
            )
    )
    private void newArmorSystem$multiplicativeJumpSafeFall(AttributeInstance instance, AttributeModifier modifier,
                                                           AttributeMap attributeMap, int amplifier) {
        boolean isJumpSafeFall = instance.getAttribute().value() == Attributes.SAFE_FALL_DISTANCE.value()
                && NEW_ARMOR_SYSTEM$JUMP_BOOST_SAFE_FALL_ID.equals(modifier.id());
        if (isJumpSafeFall) {
            double growth = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.fallJumpSafeGrowth.get() : 0.2;
            if (growth > 0.0) {
                // ADD_MULTIPLIED_BASE：基础值 × amount；amount 按原版规则随等级线性放大
                instance.addPermanentModifier(new AttributeModifier(
                        modifier.id(),
                        growth * (amplifier + 1),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            return; // 配置为 0（或不再写入原版 +1/级）时，跳跃提升对安全高度无影响
        }
        instance.addPermanentModifier(modifier);
    }
}
