package dev.newarmorsystem.mixin;

import com.mojang.logging.LogUtils;
import dev.newarmorsystem.api.MixinPriorities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 破甲（Sundering）兼容 —— 当 ApothicAttributes 的 {@code LivingEntityMixin} 被禁用时
 * （例如整合包用 Mixin Helper 禁掉它以让 NAS 的核心公式生效），复刻其破甲数学，
 * 使神化的破甲药水重新生效。
 *
 * <p><b>破甲的语义（由字节码实证，且与上游 Javadoc 逐字一致）</b>：
 * <pre>
 *   "Sundering is the inverse of resistance. It increases damage taken by 20%/level.
 *    Each point of sundering cancels out a single point of resistance, if present."
 * </pre>
 * 原版 {@code LivingEntity#getDamageAfterMagicAbsorb} 里：
 * <pre>
 *   float f = (25 - 5 * L) * 0.04F * damage;      // L = 抗性等级 ⇒ f = (1 - 0.2L) × damage
 *   return Math.max(f, 0.0F);
 * </pre>
 * 神化把其中三次调用重定向，改成：
 * <pre>
 *   return Math.max((1 - 0.2L) * damage + 0.2 * S * damage, 0.0F);   // S = 破甲等级
 *          = (1 - 0.2 * (L - S)) * damage
 * </pre>
 * ⇒ 每级破甲 +20% 受伤，恰好逐级抵消抗性的 −20%；没有抗性时也照常增伤（破甲 V = 2 倍受伤）。
 *
 * <p><b>互锁原理（无需配置开关，也不做运行时探测）</b>：
 * <ul>
 *   <li>三条 {@code @Redirect} 与 ApothicAttributes 的那三条<b>锚点完全相同</b>
 *       （同在 {@code getDamageAfterMagicAbsorb} 内的 {@code hasEffect} / {@code getAmplifier} / {@code Math.max}）；</li>
 *   <li>{@code @Redirect} 的语义是<b>替换</b>被重定向的调用指令 ⇒ 先应用者把指令换成"调用自己的 handler"，
 *       后应用者的锚点随之消失；</li>
 *   <li>本类优先级取 {@link MixinPriorities#FINAL}（{@code Integer.MAX_VALUE}，最后应用），
 *       而 ApothicAttributes 的 {@code LivingEntityMixin} 未声明优先级 ⇒ 默认 1000（1.20.1 已实证）
 *       ⇒ <b>本类永远后应用</b>；</li>
 *   <li>三条注入全部 {@code require = 0}（{@code expect} 保持默认 1）⇒ 若锚点已被对方消耗（即对方活着），
 *       本类零匹配、由 Mixin 告警提示 ⇒ <b>不崩、不双重生效</b>；若对方被禁，本类独占锚点 ⇒ 生效。</li>
 * </ul>
 * ⇒ 因此"是否开启"完全由"对方在不在"自动决定，加配置开关只会增加整合包作者工作量，故不做开关。
 *
 * <p><b>注册名</b>：写死 {@value #NEW_ARMOR_SYSTEM$SUNDERING_ID}。该效果由 Apothic Attributes 注册，
 * 其 <b>modId 在 1.21 分支改过名</b>（1.20.1 为 {@code attributeslib}、1.21.1 起为
 * {@code apothic_attributes}，见该分支 {@code gradle.properties}）—— 本类是 1.20.1 版，只需前者；
 * 1.21.1 的同名类用候选表同时容纳两个 id。查不到即空转（整合包未装前置时属于正常情况）。
 *
 * <p><b>与 NAS 其它功能零交互</b>：本类只碰 {@code getDamageAfterMagicAbsorb}（抗性行），
 * 而 NAS 的核心公式覆写的是 {@code CombatRules#getDamageAfterAbsorb(FFF)}（护甲行），两条链不相交。
 *
 * @author THEREDK
 */
@Mixin(value = LivingEntity.class, priority = MixinPriorities.FINAL)
public abstract class SunderingCompatMixin {

    @Unique
    private static final Logger NEW_ARMOR_SYSTEM$LOGGER = LogUtils.getLogger();

    /** 破甲效果的注册名（1.20.1 的 modId = attributeslib；1.21.1 起改为 apothic_attributes）。 */
    @Unique
    private static final String NEW_ARMOR_SYSTEM$SUNDERING_ID = "attributeslib:sundering";

    /** 懒查缓存。注册表在进入世界前已冻结，故首次用到时查一次即可（重复查询只是浪费，无正确性影响）。 */
    @Unique
    private static MobEffect NEW_ARMOR_SYSTEM$SUNDERING;
    @Unique
    private static boolean NEW_ARMOR_SYSTEM$SUNDERING_RESOLVED;
    /** 一次性自检日志标志（benign race：最坏多打一行）。 */
    @Unique
    private static boolean NEW_ARMOR_SYSTEM$LOGGED;

    /**
     * 一次性自检：报告本兼容处于哪种状态。放在 HEAD 是因为该锚点永不被 {@code @Redirect} 消耗，
     * 无论本类的三条 redirect 是否生效，它都会跑到。
     *
     * <p><b>回调类型必须是 {@link CallbackInfoReturnable} 而不是 {@code CallbackInfo}</b>：
     * Mixin 规定 —— 目标方法返回 void 时用 {@code CallbackInfo}，<b>返回任意值时必须用
     * {@code CallbackInfoReturnable<T>}</b>（{@code T} = 目标方法的返回类型）。本方法返回
     * {@code float} ⇒ {@code CallbackInfoReturnable<Float>}；写成 {@code CallbackInfo} 会被
     * 注解处理器判定 "Method signature does not match expected signature for Inject"。
     * 本 handler 不取消、不改返回值，故形参只是占位。
     */
    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), require = 0)
    private void newArmorSystem$sunderingSelfCheck(DamageSource pSource, float pDamage,
                                                   CallbackInfoReturnable<Float> cir) {
        if (NEW_ARMOR_SYSTEM$LOGGED) {
            return;
        }
        NEW_ARMOR_SYSTEM$LOGGED = true;

        MobEffect fx = newArmorSystem$sunderingEffect();
        if (fx == null) {
            NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: {} not found, feature inactive "
                    + "(Apothic Attributes not installed?).", NEW_ARMOR_SYSTEM$SUNDERING_ID);
            return;
        }
        if (newArmorSystem$apothicAttributesActive()) {
            NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: {} exists, but ApothicAttributes' "
                    + "LivingEntityMixin is applied (native handling) - NAS compat inactive.",
                    NEW_ARMOR_SYSTEM$SUNDERING_ID);
            return;
        }
        NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: ACTIVE - Sundering math is handled by "
                + "New Armor System (ApothicAttributes' LivingEntityMixin is not applied).");
    }

    /**
     * ① 让原版"有没有抗性效果"的检查在目标带破甲时恒真 —— 否则没有抗性效果的目标根本走不到破甲那一行。
     * 仅当目标确实带着破甲时才返回 true，否则原样返回，保证未带破甲的目标行为与原版逐位一致。
     *
     * <p><b>签名规则</b>（实证自 ApothicAttributes 的同名 handler）：{@code @Redirect} 的 handler
     * 参数 = 被重定向方法的完整签名（实例方法含 {@code this}）+ 外层方法的参数追加。
     * 故这里是 {@code (LivingEntity, MobEffect)}，且必须自行复刻原调用（{@code @Redirect} 拿不到原返回值）。
     */
    @Redirect(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"),
            require = 0)
    private boolean newArmorSystem$sunderingEntersResistBranch(LivingEntity self, MobEffect effect) {
        MobEffect fx = newArmorSystem$sunderingEffect();
        if (fx != null && self.hasEffect(fx)) {
            return true;
        }
        return self.hasEffect(effect);
    }

    /**
     * ② null 安全包装：① 会强制走进抗性分支，而此时目标可能没有抗性效果
     * ⇒ 原版 {@code getEffect(DAMAGE_RESISTANCE)} 返回 null ⇒ 直接 {@code .getAmplifier()} 会 NPE。
     * 返回 −1 ⇒ 等级按 0 计（{@code (1 - 0.2 * 0) = 1}，即不减免），与原版"无抗性"口径一致。
     */
    @Redirect(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;getAmplifier()I"),
            require = 0)
    private int newArmorSystem$sunderingAmplifier(MobEffectInstance pInstance) {
        return pInstance == null ? -1 : pInstance.getAmplifier();
    }

    /**
     * ③ 真正的数学：{@code Math.max(value + damage * (破甲等级 + 1) * 0.2F, max)}。
     * 未带破甲的目标原样调用 {@code Math.max}，行为与原版一致。
     *
     * <p>用 {@code getEffect} 取实例（而非 {@code hasEffect} + {@code getEffect} 两跳）：
     * 前者一次拿到"有没有 + 等级"，且天然 null 安全（{@code hasEffect(x)} 与
     * {@code getEffect(x) != null} 等价）。
     */
    @Redirect(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"),
            require = 0)
    private float newArmorSystem$sunderingApply(float value, float max, DamageSource source, float damage) {
        MobEffect fx = newArmorSystem$sunderingEffect();
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectInstance inst = fx == null ? null : self.getEffect(fx);
        if (inst == null) {
            return Math.max(value, max);
        }
        int level = inst.getAmplifier() + 1;
        return Math.max(value + damage * level * 0.2F, max);
    }

    /** 懒查破甲效果（写死注册名）。 */
    @Unique
    private static MobEffect newArmorSystem$sunderingEffect() {
        if (!NEW_ARMOR_SYSTEM$SUNDERING_RESOLVED) {
            ResourceLocation id = ResourceLocation.tryParse(NEW_ARMOR_SYSTEM$SUNDERING_ID);
            NEW_ARMOR_SYSTEM$SUNDERING = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
            NEW_ARMOR_SYSTEM$SUNDERING_RESOLVED = true;
        }
        return NEW_ARMOR_SYSTEM$SUNDERING;
    }

    /**
     * ApothicAttributes 的 {@code LivingEntityMixin} 是否已生效 —— 看它注入的 handler
     * 是否被合并进了 {@code LivingEntity}（运行时该类已完全变换，反射可靠）。
     * 仅用于日志措辞，不影响功能正确性。
     */
    @Unique
    private static boolean newArmorSystem$apothicAttributesActive() {
        try {
            LivingEntity.class.getDeclaredMethod("apoth_sunderingApplyEffect",
                    float.class, float.class, DamageSource.class, float.class);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
