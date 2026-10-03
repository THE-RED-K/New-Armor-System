package dev.newarmorsystem.api;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 本模组的核心战斗计算：<b>护甲减伤公式</b>与<b>护甲耐久损耗公式</b>。
 *
 * <p>这两条公式是本模组「<b>定律级</b>」的地基 —— 减伤由 {@code mixin.CombatRulesMixin}
 * 以 {@code @Overwrite} 整体接管原版 {@code CombatRules#getDamageAfterAbsorb}，
 * 耐久损耗由 {@code mixin.LivingEntityHurtEquipmentMixin} 接管
 * {@code LivingEntity#doHurtEquipment}（1.21 起护甲损耗已不经过 {@code Inventory}），
 * 因此全游戏一切实体（玩家与生物）的护甲减伤与护甲损耗都由它们唯一裁决，
 * 且<b>不可关闭</b>（{@code feature_toggles} 中不存在它们的开关，见 {@code Config} 的说明）。
 * 其余所有子系统（质量、移速、击退抗性、摔落伤害、反伤、耐久附魔重做……）都建立在这两条公式之上，
 * 并各自提供独立开关。
 *
 * <p><b>破甲附魔的语义归属</b>：原版"降低护甲减伤比例"的破甲在本模组<b>没有执行路径</b>
 * （其唯一消费点 {@code CombatRules#getDamageAfterAbsorb} 已被 {@code CombatRulesMixin} 接管），
 * 故重做归入本模组的「<b>耐久损耗</b>」破甲语言：重锤下落攻击时无视韧性、每级 +20% 护甲耐久
 * 损耗倍率、且至少磨损 1 点（见 {@link #getBreachDurabilityLoss}）。
 *
 * <p><b>破甲重做不可关闭</b>：护甲减伤公式恒开启 ⇒ 原版破甲语义没有执行路径，而破甲必须落进
 * 本模组三条合法破甲语言之一（这里选了「耐久损耗」）。因此 {@code [breach_redefinition]} 只有
 * <b>四个塑形配置、没有总开关</b>；第三方若要用事件（{@link ArmorReduceEvent}、原生
 * {@code ArmorHurtEvent}、{@link ArmorReflectEvent}）绕过本模组的减伤/损耗公式再自建破甲，
 * 改这四个配置即可，无需总开关。
 *
 * <p>为此随包覆写两个原版数据文件（仅 1.21.1）：{@code data/minecraft/enchantment/breach.json}
 * 去掉 {@code minecraft:armor_effectiveness} 效果（改由上面的代码实现）并去掉互斥集，
 * {@code density.json} 效果与数值原样保留、仅去掉互斥集 —— 原版二者的互斥来自
 * {@code #minecraft:exclusive_set/damage}（同时含 {@code density} 与 {@code breach}），
 * 且兼容性判定是<b>对称</b>的（任一方列出对方即互斥），故两个文件都要覆写，
 * 破甲与致密才能共存于同一把重锤。
 */
public class NewCombatRules {

    /** 破甲重击每级增加的护甲耐久损耗倍率（{@code breach_redefinition} 配置未加载时的默认值）。 */
    private static final double BREACH_DURABILITY_LOSS_PER_LEVEL = 0.2;

    /** 破甲重击是否无视韧性（{@code breach_redefinition} 配置未加载时的默认值）。 */
    private static final boolean BREACH_IGNORES_TOUGHNESS = true;

    /** 破甲重击是否至少磨损 1 点（{@code breach_redefinition} 配置未加载时的默认值）。 */
    private static final boolean BREACH_AT_LEAST_ONE_DURABILITY = true;

    /**
     * <b>本模组的护甲减伤公式 —— 定律级公式，不是可选项。</b>
     *
     * <p>本方法的结果由 {@code mixin.CombatRulesMixin} 以 {@code @Overwrite} 接管原版
     * {@code CombatRules#getDamageAfterAbsorb(float, float, float)} 后返回：<b>全游戏一切实体的
     * 护甲减伤都由此公式唯一裁决</b>，原版公式在本模组中已不存在。它属于本模组
     * <b>不可关闭的核心功能</b>（与「护甲耐久损耗公式」并列，{@code feature_toggles} 里没有开关），
     * 玩家与所有生物一视同仁。
     *
     * <p><b>公式结构（按伤害量划分的三段式）</b> —— 先由护甲值与韧性算出两个阈值：
     * <pre>
     *   L = armor × lowZoneCoefficient × toughness                       （低伤区上界）
     *   H = armor × (highZoneConstant + highZoneCoefficient × toughness)  （高伤区下界）
     * </pre>
     * 再按伤害 {@code D} 落在哪一段取减伤率：
     * <ul>
     *   <li>{@code D ≤ L}：减伤 = {@code maxReduction} —— 低伤区，吃满上限；</li>
     *   <li>{@code L < D < H}：减伤在 {@code minReduction} 与 {@code maxReduction} 之间
     *       随 {@code D} 线性插值 —— 过渡区，伤害越大减伤越低；</li>
     *   <li>{@code D ≥ H}：减伤 = {@code minReduction} —— 高伤区，落到下限。</li>
     * </ul>
     * 最终返回 {@code D × (1 − 减伤)}。韧性低于 {@code armorToughnessMinAllowed} 时先取值下限
     * （该下限可为负数，用于表达"负韧性 = 更怕打"）。
     *
     * <p><b>与原版公式的根本差异</b>：原版为
     * {@code DR = clamp(armor − damage / (2 + toughness / 4), armor / 5, 20) / 25}，
     * 其减伤率只与"护甲、韧性、伤害"的一次关系相关，且<b>上限被写死在 80%</b>（20/25）、
     * 下限被写死在 0%，无法表达"重甲对小怪几乎免疫、面对重击却明显退让"这类梯度。
     * 本公式把减伤率显式参数化为<b>上下限 + 两个阈值</b>，使整套减伤曲线可由配置精确塑形
     * —— 这正是本模组护甲体系的地基，故为定律级。
     *
     * <p><b>出口</b>：结算完成、返回原版伤害管线<b>之前</b>，{@code CombatRulesMixin} 会派发
     * {@link ArmorReduceEvent}（监听者可改写结算后伤害）；无监听者时行为与不派发完全一致。
     * 需要"受击实体"上下文的监听者请配合 NeoForge 原生 {@code LivingHurtEvent}
     * （本公式<b>之前</b>触发，可改原始伤害）与 {@code LivingDamageEvent}
     * （本公式<b>之后</b>触发，可改最终伤害）使用。
     *
     * @param damage    The amount of damage.
     * @param armor     The amount of armor points the target has.
     * @param toughness The amount of armor toughness points the target has.
     * @return The modified damage value after applying armor.
     */
    public static float getDamageAfterArmor(float damage, float armor, float toughness) {
        if (armor <= 0) return damage;
        if (toughness < Config.COMMON.armorToughnessMinAllowed.get()) {
            toughness = Config.COMMON.armorToughnessMinAllowed.get();
        }

        double maxReduction = Config.COMMON.maxReduction.get();
        // 减伤下限：高伤区不再写死为 0（旧行为等价于 minReduction = 0.0）。
        // 防御性钳制：下限不允许高于上限，否则公式会反转（减伤随伤害上升）
        double minReduction = Math.min(Config.COMMON.minReduction.get(), maxReduction);

        double lowZoneThreshold = armor
                * Config.COMMON.lowZoneCoefficient.get()
                * toughness;
        double highZoneThreshold = armor
                * (Config.COMMON.highZoneConstant.get()
                + Config.COMMON.highZoneCoefficient.get() * toughness);

        double reduction;
        if (damage <= lowZoneThreshold) {
            reduction = maxReduction;
        } else if (damage < highZoneThreshold && highZoneThreshold > lowZoneThreshold) {
            // 两阈值之间：从 maxReduction 线性降到 minReduction（旧版固定降到 0）
            reduction = minReduction + (maxReduction - minReduction)
                    * (highZoneThreshold - damage)
                    / (highZoneThreshold - lowZoneThreshold);
        } else {
            reduction = minReduction;
        }

        return (float) (damage * (1.0 - reduction));
    }

    /**
     * Gets the amount of durability loss the user would take after applying toughness.<br>
     *
     * @param damage           The amount of damage.
     * @param toughness        The amount of armor toughness points the target has.
     * @param unbreakingPresent Whether the armor piece has an Unbreaking enchantment.
     *                          {@code true}: no rounding is done here, the raw quotient
     *                          {@code damage / denominator} is returned so that the single
     *                          {@code (int)} truncation done by the caller is the only rounding
     *                          point of {@code floor(damage / denominator)};
     *                          {@code false}: behaves like vanilla {@code max(1, floor(...))}.
     * @return The modified damage value after applying armor.
     * The vanilla calculation is <code>Loss=damage/4.0F</code>
     */
    public static float getDurabilityLoss(float damage, float toughness, boolean unbreakingPresent) {
        if (Config.COMMON_SPEC.isLoaded()) {
            if (damage <= 0.0F) return 0.0F;
            if (toughness < Config.COMMON.armorToughnessMinAllowed.get()) {
                toughness = Config.COMMON.armorToughnessMinAllowed.get();
            }
            double denominator = (Config.COMMON.durabilityReductionConstant.get()
                    + Config.COMMON.toughnessCoefficient.get() * toughness);
            if (denominator <= 0.0) denominator = 0.1;
            double loss = damage / denominator;
            if (unbreakingPresent) {
                // 舍入职责全部交给施加损耗时的 (int) 截断，避免双重舍入
                return (float) loss;
            }
            return (float) Math.max(1.0, Math.floor(loss));
        } else {
            return (damage / 4.0F);
        }
    }

    /**
     * Gets the armor toughness provided by a single armor piece via its attribute modifiers.<br>
     *
     * <p>1.21.1 起护甲属性改为数据组件（{@code DataComponents.ATTRIBUTE_MODIFIERS}），
     * 条目为 {@link ItemAttributeModifiers.Entry}（{@code Holder<Attribute>} + 槽位组），
     * 不再有「按槽位查询的 Multimap」接口，故此处按槽位组过滤求和。
     *
     * @param stack The armor item stack.
     * @param slot  The equipment slot the piece is worn in.
     * @return The sum of the piece's {@link Attributes#ARMOR_TOUGHNESS} modifiers for that slot.
     * For vanilla armor: leather/iron/gold = 0, diamond = 2, netherite = 3.
     */
    public static float getPieceToughness(ItemStack stack, EquipmentSlot slot) {
        float toughness = 0.0F;
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().value() == Attributes.ARMOR_TOUGHNESS.value() && entry.slot().test(slot)) {
                toughness += (float) entry.modifier().amount();
            }
        }
        return toughness;
    }

    /**
     * 读取物品的耐久附魔（Unbreaking）等级。
     *
     * <p>通过 {@code Holder#is(ResourceKey)} 匹配，无需访问注册表，故可在任意上下文调用。
     *
     * @param stack 待检查的物品堆
     * @return 耐久附魔等级；未附魔返回 0
     */
    public static int getUnbreakingLevel(ItemStack stack) {
        if (BrokenState.isBroken(stack)) {
            return 0;   // broken 物品附魔全部失效；本处直读数据组件，绕过了 EnchantmentHelper 的拦截
        }
        ItemEnchantments enchantments = stack.getTagEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.UNBREAKING)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    /**
     * 读取物品的荆棘附魔（Thorns）等级 —— 反伤比例的第二来源
     * （每级 + 系数，默认 0.15，见 {@link DamageReflection#thornsCoefficient()}）。
     *
     * <p>与 {@link #getUnbreakingLevel} 同法：通过 {@code Holder#is(ResourceKey)} 匹配，
     * 无需访问注册表，故可在任意上下文调用。
     *
     * @param stack 待检查的物品堆
     * @return 荆棘附魔等级；未附魔返回 0
     */
    public static int getThornsLevel(ItemStack stack) {
        if (BrokenState.isBroken(stack)) {
            return 0;   // broken 物品附魔全部失效；本处直读数据组件，绕过了 EnchantmentHelper 的拦截
        }
        ItemEnchantments enchantments = stack.getTagEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.THORNS)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    /**
     * 读取物品的破甲（Breach）等级 —— 重锤下落攻击的护甲磨损来源。
     *
     * <p><b>本模组的破甲语义（重做）</b>：原版破甲以
     * {@code EnchantmentEffectComponents.ARMOR_EFFECTIVENESS}（"降低护甲减伤比例"）实现，
     * 唯一的消费点是 {@code CombatRules#getDamageAfterAbsorb} —— 而该方法是本模组
     * {@code CombatRulesMixin} 以 {@code @Overwrite} 整体接管的方法，
     * 故<b>原版语义在本模组中已无任何执行路径</b>（检索确认 {@code LivingEntity} 等均不调用它）。
     * 破甲在本模组归入「<b>耐久损耗</b>」这一合法破甲语言：物理冲击加速护甲磨损
     * （见 {@link #getBreachDurabilityLoss}）。
     *
     * <p>与 {@link #getThornsLevel} 同法：直读数据组件、{@code broken} 物品返回 0。
     *
     * @param stack 待检查的物品堆（通常是攻击者的武器）
     * @return 破甲等级；未附魔返回 0
     */
    public static int getBreachLevel(ItemStack stack) {
        if (BrokenState.isBroken(stack)) {
            return 0;   // broken 物品附魔全部失效；本处直读数据组件，绕过了 EnchantmentHelper 的拦截
        }
        ItemEnchantments enchantments = stack.getTagEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.BREACH)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    /**
     * 读取本次伤害的破甲等级：仅当来源是<b>重锤下落攻击</b>时非 0。
     *
     * <p>判定与致密（Density）同源 —— 都用 {@link MaceItem#canSmashAttack(LivingEntity)}：
     * 攻击者 {@code fallDistance > 1.5} 且未鞘翅滑翔。时机上是安全的：该判定在
     * {@code MaceItem#postHurtEnemy}（在 {@code target.hurt} 之后才被调用，用于重击击退）
     * 中同样成立，故护甲结算时 {@code fallDistance} 尚未被重置。
     *
     * @param source 造成护甲损耗的伤害来源
     * @return 破甲等级；非重锤下落攻击返回 0
     */
    public static int getBreachLevel(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || !MaceItem.canSmashAttack(attacker)) {
            return 0;
        }
        ItemStack weapon = source.getWeaponItem();
        return weapon == null ? 0 : getBreachLevel(weapon);
    }

    /**
     * 破甲重击的护甲耐久损耗 —— 本模组破甲语言的「物理冲击磨损护甲」：<b>不改变减伤百分比</b>，
     * 只加速护甲消耗。三条规则各自独立可配（{@code [breach_redefinition]}）：
     * <ol>
     *   <li>{@code breachIgnoresToughness}（默认 true）：<b>无视韧性</b> —— 韧性按 0 代入，
     *       除数恒为 {@code durabilityReductionConstant}；关闭时与原损耗公式一样受韧性保护；</li>
     *   <li>{@code breachDurabilityLossPerLevel}（默认 0.2）：<b>每级 +20% 损耗倍率</b>，
     *       即 {@code × (1 + 系数 × 等级)}；</li>
     *   <li>{@code breachAtLeastOneDurability}（默认 true）：<b>至少磨损 1 点</b> ——
     *       公式层本就以"无耐久附魔"口径取整（{@code max(1, floor(...))}）给出 ≥ 1 的目标值，
     *       该开关决定的是<b>耐久附魔能否把这 1 点也吃掉</b>，故其判定在
     *       {@link #breachAwareHurtAmount} 里（与"是否穿透"互不依赖）。</li>
     * </ol>
     *
     * @param damage      本次损耗前的原始伤害（未经 /4）
     * @param toughness   该件护甲自身的韧性（仅 {@code breachIgnoresToughness} 关闭时参与计算）
     * @param breachLevel 破甲等级（仅在 &gt; 0 时由调用方走本方法）
     * @return 该件护甲本次的目标损耗（公式层恒 ≥ 1）
     */
    public static float getBreachDurabilityLoss(float damage, float toughness, int breachLevel) {
        double coefficient = Config.COMMON_SPEC.isLoaded()
                ? Config.COMMON.breachDurabilityLossPerLevel.get()
                : BREACH_DURABILITY_LOSS_PER_LEVEL;
        boolean ignoresToughness = !Config.COMMON_SPEC.isLoaded()
                || Config.COMMON.breachIgnoresToughness.get();
        // 以"无耐久附魔"口径取整（≥1）：韧性按 0 代入时除数为 durabilityReductionConstant，
        // 最小值保护是负数（默认 -16）不会触发
        float base = getDurabilityLoss(damage, ignoresToughness ? 0.0F : toughness, false);
        return (float) (base * (1.0 + coefficient * breachLevel));
    }

    /**
     * 破甲重击施加耐久损耗前的入参换算 —— <b>是否至少磨损 1 点</b>与<b>是否穿透耐久附魔</b>
     * 由两个开关分别控制，可只开其一、也可都开：
     * <ul>
     *   <li>{@code breachAtLeastOneDurability}（<b>默认 true</b>）：<b>保证至少 1 点</b> ——
     *       入参下限抬到 {@code 1 + 耐久系数 × 等级}，使耐久附魔减免后仍 ≥ 1。
     *       这是本附魔<b>唯一的"魔法"成分</b>：耐久附魔能让损耗变小，但不能让它彻底消失；</li>
     *   <li>{@code breachPiercesUnbreaking}（<b>默认 false</b>，开启后过强）：<b>完全穿透</b> ——
     *       入参放大到 {@code ceil(目标 × (1 + 耐久系数 × 等级))}，经耐久附魔重做
     *       {@code floor(amount / (1 + 系数 × 等级))} 后仍<b>实打实扣掉目标值</b>；</li>
     *   <li>两者都关闭：原样返回，交给耐久附魔正常减免（可能为 0）；</li>
     *   <li>两者都开启：以穿透为准（穿透已保证不少于目标值，自然满足"至少 1 点"）。</li>
     * </ul>
     *
     * <p><b>没有耐久附魔时不会做任何补偿</b>（{@code unbreakingLevel <= 0} 直接原样返回），
     * 因此不存在"无耐久附魔却预补偿"的情况。
     *
     * <p>预补偿只影响内部的 {@code hurtAndBreak} 入参：事件（{@code ArmorHurtEvent}）与反伤
     * 用的是补偿前的真实损耗，监听者看到的数值不受影响。
     * 耐久附魔重做关闭（{@code feature_toggles.unbreakingRedefinitionEnabled = false}）时
     * 无法预知原版概率性减免，原样返回（此时穿透无从保证，属整合包的开关选择）。
     *
     * @param targetLoss  该件护甲本次的目标损耗
     * @param breachLevel 破甲等级
     * @param stack       该件护甲
     * @return 交给 {@code hurtAndBreak} 的入参
     */
    public static int breachAwareHurtAmount(int targetLoss, int breachLevel, ItemStack stack) {
        if (breachLevel <= 0
                || !Config.COMMON_SPEC.isLoaded()
                || !Config.COMMON.unbreakingRedefinitionEnabled.get()) {
            return targetLoss;
        }
        int unbreakingLevel = getUnbreakingLevel(stack);
        if (unbreakingLevel <= 0) {
            return targetLoss;   // 没有耐久附魔：不存在减免，也就不做任何预补偿
        }
        double factor = 1.0 + Config.COMMON.unbreakingCoefficient.get() * unbreakingLevel;
        if (Config.COMMON.breachPiercesUnbreaking.get()) {
            return (int) Math.ceil(targetLoss * factor);
        }
        if (Config.COMMON.breachAtLeastOneDurability.get()) {
            // 只保证"减免后仍 ≥ 1"：入参下限 = 1 + 耐久系数 × 等级
            return (int) Math.max(targetLoss, Math.ceil(factor));
        }
        return targetLoss;
    }

    /**
     * 判断该护甲是否附有耐久附魔（Unbreaking）。
     *
     * <p>仅用于决定耐久损耗公式的舍入职责（见 {@link #getDurabilityLoss}），不改变损耗量本身；
     * 1.21.1 的耐久附魔实际减免发生在 {@code ItemStack#hurtAndBreak} →
     * {@code EnchantmentHelper#processDurabilityChange}（由「耐久附魔修改」模块接管）。
     *
     * @param stack 待检查的物品堆
     * @return 是否附有耐久附魔（等级 ≥ 1）
     */
    public static boolean hasUnbreaking(ItemStack stack) {
        return getUnbreakingLevel(stack) > 0;
    }
}
