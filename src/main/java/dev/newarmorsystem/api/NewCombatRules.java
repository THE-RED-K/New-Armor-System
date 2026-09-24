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
 * Contains New Armor System's calculations for armor values.
 */
public class NewCombatRules {

    /**
     * Gets the amount of damage the user would take after applying armor, toughness.<br>
     *
     * @param damage    The amount of damage.
     * @param armor     The amount of armor points the target has.
     * @param toughness The amount of armor toughness points the target has.
     * @return The modified damage value after applying armor.
     * The vanilla calculation is <code>DR = clamp(armor - damage / (2 + toughness / 4), armor / 5, 20) / 25</code>
     */
    public static float getDamageAfterArmor(float damage, float armor, float toughness) {
        if (armor <= 0) return damage;
        if (toughness < Config.COMMON.armorToughnessMinAllowed.get()) {
            toughness = Config.COMMON.armorToughnessMinAllowed.get();
        }

        double maxReduction = Config.COMMON.maxReduction.get();

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
            reduction = maxReduction * (highZoneThreshold - damage)
                    / (highZoneThreshold - lowZoneThreshold);
        } else {
            reduction = 0.0;
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
