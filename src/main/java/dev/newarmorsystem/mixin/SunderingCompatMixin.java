package dev.newarmorsystem.mixin;

import com.mojang.logging.LogUtils;
import dev.newarmorsystem.api.MixinPriorities;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
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
 * <p><b>本类是 1.20.1 版的移植</b>，行为与语义完全一致。1.20.1 → 1.21.1 的差异：
 * <ol>
 *   <li><b>效果 API 改为 Holder 形态</b>：{@code hasEffect} / {@code getEffect} 接收
 *       {@link Holder}{@code <MobEffect>}；软查相应改为 {@code Registry#getHolder}；</li>
 *   <li><b>无 refmap</b>：NeoForge 1.20.5+ 已改用官方 Mojang 映射、开发期与运行期方法名一致
 *       ⇒ 注解里的目标字符串即最终名称（本类因此也不加 {@code remap}）；</li>
 *   <li><b>注册名前缀不同</b>：Apothic Attributes 的 modId 在 1.21 分支由 {@code attributeslib}
 *       改为 {@code apothic_attributes}（见该分支 {@code gradle.properties}），故 {@value #NEW_ARMOR_SYSTEM$SUNDERING_IDS}
 *       用<b>候选表</b>同时容纳两个 id —— 这是 2026-10-23 核实时发现并修掉的坑：只写旧 id 会让
 *       本类在 1.21.1 上静默失效（{@code getHolder} 返回空 ⇒ 三条 handler 全部走原样返回）；</li>
 *   <li><b>无需 R1 守卫</b>：1.21.1 的耐久附魔重做住在 {@code EnchantmentHelper#processDurabilityChange}
 *       的 HEAD 取消式注入里（{@code cir.setReturnValue(reduced)}，原版体不执行、下游按 {@code > 0} 判定）
 *       ⇒ 不存在 1.20.1 那种"损耗被压到 0 后走到原版尾部误判物品损坏"的路径。</li>
 * </ol>
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
 *
 * <p><b>互锁原理（无需配置开关，也不做运行时探测）</b>：
 * <ul>
 *   <li>三条 {@code @Redirect} 与 ApothicAttributes 的那三条<b>锚点完全相同</b>
 *       （同在 {@code getDamageAfterMagicAbsorb} 内的 {@code hasEffect} / {@code getAmplifier} / {@code Math.max}）；
 *       <b>已对上游 1.21 分支源码逐条核实</b>：三条注解的 method / at / target 与本类完全一致，
 *       对方 {@code @Mixin} 同样未声明优先级（默认 1000）⇒ 互锁前提成立；</li>
 *   <li>{@code @Redirect} 的语义是<b>替换</b>被重定向的调用指令 ⇒ 先应用者消耗锚点，后应用者找不到目标；</li>
 *   <li>本类优先级取 {@link MixinPriorities#FINAL} ⇒ <b>永远后应用</b>；</li>
 *   <li>三条注入全部 {@code require = 0} ⇒ 锚点已被对方消耗时零匹配、由 Mixin 告警提示
 *       ⇒ <b>不崩、不双重生效</b>；对方被禁时本类独占锚点 ⇒ 生效。</li>
 * </ul>
 *
 * <p><b>日志</b>：首次进入抗性结算时打印一行，说明本兼容处于三种状态中的哪一种
 * （未装前置 / 对方原生接管 / NAS 接管），把静默状态变成可见。
 *
 * <p><b>与 NAS 其它功能零交互</b>：本类只碰 {@code getDamageAfterMagicAbsorb}（抗性行），
 * 而 NAS 的核心公式覆写的是
 * {@code CombatRules#getDamageAfterAbsorb(LivingEntity,float,DamageSource,float,float)}（护甲行，
 * 1.21.1 起签名已扩展为带实体），两条链不相交。
 *
 * @author THEREDK
 */
@Mixin(value = LivingEntity.class, priority = MixinPriorities.FINAL)
public abstract class SunderingCompatMixin {

    @Unique
    private static final Logger NEW_ARMOR_SYSTEM$LOGGER = LogUtils.getLogger();

    /**
     * 破甲效果的注册名候选表（写死；按顺序取第一个命中的）。
     *
     * <p>两侧注册 path 均为 {@code "sundering"}
     * （{@code ALObjects.MobEffects.SUNDERING = R.effect("sundering", SunderingEffect::new)}），
     * 差别只在命名空间：1.20.1 的 modId 是 {@code attributeslib}，1.21.1 起改为
     * {@code apothic_attributes}（上游 1.21 分支 {@code gradle.properties} 实证）。
     */
    @Unique
    private static final String[] NEW_ARMOR_SYSTEM$SUNDERING_IDS = {
            "attributeslib:sundering",
            "apothic_attributes:sundering",
    };

    /** 懒查缓存。注册表在进入世界前已冻结，故首次用到时查一次即可（重复查询只是浪费，无正确性影响）。 */
    @Unique
    private static Holder<MobEffect> NEW_ARMOR_SYSTEM$SUNDERING;
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

        Holder<MobEffect> fx = newArmorSystem$sunderingEffect();
        if (fx == null) {
            NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: [{}] not found, feature inactive "
                    + "(Apothic Attributes not installed?).", String.join(" / ", NEW_ARMOR_SYSTEM$SUNDERING_IDS));
            return;
        }
        if (newArmorSystem$apothicAttributesActive()) {
            NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: [{}] exists, but ApothicAttributes' "
                    + "LivingEntityMixin is applied (native handling) - NAS compat inactive.",
                    String.join(" / ", NEW_ARMOR_SYSTEM$SUNDERING_IDS));
            return;
        }
        NEW_ARMOR_SYSTEM$LOGGER.info("[NewArmorSystem] sundering compat: ACTIVE - Sundering math is handled by "
                + "New Armor System (ApothicAttributes' LivingEntityMixin is not applied).");
    }

    /**
     * ① 让原版"有没有抗性效果"的检查在目标带破甲时恒真 —— 否则没有抗性效果的目标根本走不到破甲那一行。
     * 仅当目标确实带着破甲时才返回 true，否则原样返回，保证未带破甲的目标行为与原版逐位一致。
     *
     * <p><b>签名规则</b>（实证自 ApothicAttributes 的同名 handler，1.20.1 与 1.21 分支一致）：
     * {@code @Redirect} 的 handler 参数 = 被重定向方法的完整签名（实例方法含 {@code this}）
     * + 外层方法的参数追加。1.21.1 起 {@code hasEffect} 接收 {@link Holder}{@code <MobEffect>}，
     * 故这里是 {@code (LivingEntity, Holder)}，且必须自行复刻原调用（{@code @Redirect} 拿不到原返回值）。
     */
    @Redirect(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"),
            require = 0)
    private boolean newArmorSystem$sunderingEntersResistBranch(LivingEntity self, Holder<MobEffect> effect) {
        Holder<MobEffect> fx = newArmorSystem$sunderingEffect();
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
        Holder<MobEffect> fx = newArmorSystem$sunderingEffect();
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectInstance inst = fx == null ? null : self.getEffect(fx);
        if (inst == null) {
            return Math.max(value, max);
        }
        int level = inst.getAmplifier() + 1;
        return Math.max(value + damage * level * 0.2F, max);
    }

    /** 懒查破甲效果（按候选表取第一个命中的）。1.21.1 用 {@code Registry#getHolder} 拿 {@link Holder}。 */
    @Unique
    private static Holder<MobEffect> newArmorSystem$sunderingEffect() {
        if (!NEW_ARMOR_SYSTEM$SUNDERING_RESOLVED) {
            for (String raw : NEW_ARMOR_SYSTEM$SUNDERING_IDS) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) {
                    continue;
                }
                Holder<MobEffect> found = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (found != null) {
                    NEW_ARMOR_SYSTEM$SUNDERING = found;
                    break;
                }
            }
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
