package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 护甲韧性（armor_toughness）与击退抗性（knockback_resistance）允许的最小值
 * 改为可配置（护甲韧性默认 -16；击退抗性默认无下限，关闭无下限后默认 -2.0；原版均为 0）。
 *
 * <p>击退抗性放宽到负数是为了给附属模组留接口：原版击退公式
 * （{@code LivingEntity.knockback} 中 {@code strength *= 1.0 - resistance}）对负数
 * 天然成立 —— 负抗性 = 击退更远（-1.0 时击退强度 ×2）。属性范围不放开的话，
 * 附属模组添加的负修饰符会被 {@link RangedAttribute#sanitizeValue} clamp 成 0，效果无法生效。
 *
 * <p><b>1.20.1 → 1.21.1 的接管点迁移（必须改）</b>：1.20.1 的 {@code sanitizeValue} 调用的是
 * {@code getMinValue()}，故模组只需覆写 {@code getMinValue} 的返回值；1.21.1 的
 * {@code sanitizeValue} <b>直接读私有字段 {@code minValue}</b>：
 * <pre>{@code return Double.isNaN(value) ? this.minValue : Mth.clamp(value, this.minValue, this.maxValue);}</pre>
 * 覆写 {@code getMinValue()} 已完全无效，故接管点迁移到 {@link RangedAttribute#sanitizeValue} 的 HEAD。
 *
 * <p><b>为何不是 {@code @Overwrite}</b>：仅对目标属性改写，其余属性按原参数返回、保持原版逻辑，
 * 与其它模组在 {@code sanitizeValue} 上的注入共存。
 *
 * <p><b>NeoForge 差异</b>：1.21 起 {@code Attributes.KNOCKBACK_RESISTANCE} 用的是
 * NeoForge 的 {@code PercentageAttribute}（百分比显示），它 {@code extends RangedAttribute}
 * 且未覆写 {@code sanitizeValue}，故本 Mixin 同样覆盖它。
 *
 * <p>构造器里只比较描述 ID、不读配置：{@code Attributes} 的静态初始化发生在 Bootstrap 阶段，
 * 早于模组构造与配置加载，此时读 {@link Config} 会抛
 * "Cannot get config value before config is loaded"。同理，
 * {@code sanitizeValue} 内先检查配置是否已加载。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    /**
     * mixin 私有标志字段：构造器里根据参数判断当前实例是否为 armor_toughness。
     * 不能 {@code @Shadow} 继承自父类 {@code Attribute} 的 descriptionId 字段 ——
     * Mixin 无法为继承字段可靠解析混淆映射。
     */
    @Unique
    private boolean newArmorSystem$isArmorToughness;

    /** 构造器里标记的 knockback_resistance（击退抗性）实例。 */
    @Unique
    private boolean newArmorSystem$isKnockbackResistance;

    /**
     * 击退抗性"无下限"哨兵值：开启无下限开关时作为最小值，
     * 任何有限修饰符值都不会被 clamp 截断。
     */
    @Unique
    private static final double NEW_ARMOR_SYSTEM$NO_KNOCKBACK_LOWER_LIMIT = -Double.MAX_VALUE;

    /** 构造器标记：只比较描述 ID，不读配置（原因见类注释）。 */
    @Unique
    @Inject(
            method = "<init>(Ljava/lang/String;DDD)V",
            at = @At("RETURN"),
            require = 1
    )
    private void newArmorSystem$markAttributes(String descriptionId, double defaultValue,
                                               double minValue, double maxValue, CallbackInfo ci) {
        this.newArmorSystem$isArmorToughness = "attribute.name.generic.armor_toughness".equals(descriptionId);
        this.newArmorSystem$isKnockbackResistance = "attribute.name.generic.knockback_resistance".equals(descriptionId);
    }

    /**
     * 使 armor_toughness / knockback_resistance 的最小允许值可配置（原版固定 0）。
     *
     * <p>在 HEAD 处接管并<b>复刻原版语义</b>，仅把下界换成配置值：
     * 原版为 {@code Double.isNaN(value) ? minValue : clamp(value, minValue, maxValue)}，
     * 本方法对目标属性返回 {@code clamp(value, newMin, maxValue)}，
     * 其余属性直接放行、由原方法处理（含 NaN 分支）。
     *
     * <p>延迟到运行时读取配置：{@code sanitizeValue} 仅在属性值计算时被调用，
     * 彼时配置必然已加载；未加载时保持原值，任何极端时序下都不会崩溃。
     */
    @Unique
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 1)
    private void newArmorSystem$widenLowerBound(double value, CallbackInfoReturnable<Double> cir) {
        if (Double.isNaN(value) || !Config.COMMON_SPEC.isLoaded()) {
            return; // NaN 与原版一致交由原方法处理；配置未加载保持原值
        }
        double newMin;
        if (this.newArmorSystem$isArmorToughness) {
            newMin = Config.COMMON.armorToughnessMinAllowed.get();
        } else if (this.newArmorSystem$isKnockbackResistance) {
            // 无下限开关默认开启：返回哨兵值，负修饰符完全生效（击退更远）
            newMin = Config.COMMON.knockbackResistanceNoLowerLimit.get()
                    ? NEW_ARMOR_SYSTEM$NO_KNOCKBACK_LOWER_LIMIT
                    : Config.COMMON.knockbackResistanceMinAllowed.get();
        } else {
            return; // 其余属性保持原版范围
        }
        cir.setReturnValue(Mth.clamp(value, newMin, ((RangedAttribute) (Object) this).getMaxValue()));
    }
}
