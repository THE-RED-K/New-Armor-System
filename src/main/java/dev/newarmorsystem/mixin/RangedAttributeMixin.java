package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.Config;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 护甲韧性（armor_toughness）与击退抗性（knockback_resistance）允许的最小值
 * 改为可配置（护甲韧性默认 -16；击退抗性默认无下限，关闭无下限后默认 -2.0；
 * 原版均为 0）。
 *
 * <p>击退抗性放宽到负数是为了给附属模组留接口：原版击退公式
 * （{@code LivingEntity.knockback} 中 {@code strength *= 1.0 - resistance}）对负数
 * 天然成立 —— 负抗性 = 击退更远（-1.0 时击退强度 ×2）。属性范围不放开的话，
 * 附属模组添加的负修饰符会被 {@code sanitizeValue} clamp 成 0，效果无法生效。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    /**
     * mixin 私有标志字段：构造器里根据参数判断当前实例是否为 armor_toughness。
     * 不能 @Shadow 继承自父类 Attribute 的 descriptionId 字段 —— Mixin 0.8.5
     * 无法为继承字段解析混淆映射（会告警且运行时不可靠）。
     */
    @Unique
    private boolean newArmorSystem$isArmorToughness;

    /** 构造器里标记的 knockback_resistance（击退抗性）实例。 */
    @Unique
    private boolean newArmorSystem$isKnockbackResistance;

    /**
     * 击退抗性"无下限"哨兵值：开启无下限开关时 {@link net.minecraft.world.entity.ai.attributes.RangedAttribute#getMinValue()} 返回该值，
     * 任何有限修饰符值都不会被 {@code sanitizeValue} 的 clamp 截断。
     */
    @Unique
    private static final double NEW_ARMOR_SYSTEM$NO_KNOCKBACK_LOWER_LIMIT = -Double.MAX_VALUE;

    /**
     * 这里只比较构造器参数（dev 名），不读取配置。
     * 为什么不能在此读配置：{@link net.minecraft.world.entity.ai.attributes.Attributes}
     * 的静态初始化发生在客户端 Bootstrap 阶段，早于 mod 构造与 Forge 配置加载，
     * 此时读 {@link Config} 会抛 "Cannot get config value before config is loaded"。
     */
    @Unique
    @Inject(
            method = "<init>(Ljava/lang/String;DDD)V",
            at = @At("RETURN"),
            require = 1
    )
    private void newArmorSystem$markAttributes(
            String descriptionId,
            double defaultValue,
            double minValue,
            double maxValue,
            CallbackInfo ci
    ) {
        this.newArmorSystem$isArmorToughness = "attribute.name.generic.armor_toughness".equals(descriptionId);
        this.newArmorSystem$isKnockbackResistance = "attribute.name.generic.knockback_resistance".equals(descriptionId);
    }

    /**
     * 使 armor_toughness / knockback_resistance 的最小允许值可配置（原版固定 0）。
     *
     * <p>采用 {@code @Inject RETURN + cancellable}（而非 {@code @Overwrite}）：
     * 仅对目标属性改写返回值，其余属性保持原 {@code minValue}，与其他模组在
     * {@code getMinValue} 上的 {@code @Inject} 共存、不覆盖他人改写。
     * 延迟到 {@code getMinValue()} 读取：该方法仅在游戏运行中（实体属性 clamp 校验）被调用，
     * 彼时配置必然已加载；未加载时保持原值，任何极端时序下都不会崩溃。
     */
    @Unique
    @Inject(method = "getMinValue", at = @At("RETURN"), cancellable = true, require = 1)
    private void newArmorSystem$overrideMinValue(CallbackInfoReturnable<Double> cir) {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return; // 配置未加载：保持原值
        }
        if (this.newArmorSystem$isArmorToughness) {
            cir.setReturnValue((double) Config.COMMON.armorToughnessMinAllowed.get());
        } else if (this.newArmorSystem$isKnockbackResistance) {
            // 无下限开关默认开启：返回哨兵值，负修饰符完全生效（击退更远）
            if (Config.COMMON.knockbackResistanceNoLowerLimit.get()) {
                cir.setReturnValue(NEW_ARMOR_SYSTEM$NO_KNOCKBACK_LOWER_LIMIT);
            } else {
                // 关闭后回退到下限数值配置（默认 -2.0）
                cir.setReturnValue(Config.COMMON.knockbackResistanceMinAllowed.get());
            }
        }
    }
}
