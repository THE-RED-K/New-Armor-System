package dev.newarmorsystem.api;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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
 */
public class NewCombatRules {

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
     * （每级 +60%，见 {@code DamageReflection}）。
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
