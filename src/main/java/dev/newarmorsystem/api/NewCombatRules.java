package dev.newarmorsystem.api;


import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

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
        if (toughness < Config.COMMON.armorToughnessMinAllowed.get()) toughness = Config.COMMON.armorToughnessMinAllowed.get();

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
     *                          {@code floor} in {@link ItemStack#hurt} is the only rounding point
     *                          of {@code floor(damage / denominator / (1 + coefficient * level))};
     *                          {@code false}: behaves like vanilla {@code max(1, floor(...))}.
     * @return The modified damage value after applying armor.
     * The vanilla calculation is <code>Loss=damage/4.0F</code>
     */
    public static float getDurabilityLoss(float damage, float toughness, boolean unbreakingPresent) {
        if(Config.COMMON_SPEC.isLoaded()){
            if(damage <= 0.0F) return 0.0F;
            if(toughness < Config.COMMON.armorToughnessMinAllowed.get()) toughness = Config.COMMON.armorToughnessMinAllowed.get();
            double denominator = (Config.COMMON.durabilityReductionConstant.get()
                    + Config.COMMON.toughnessCoefficient.get() * toughness);
            if (denominator <= 0.0) denominator = 0.1;
            double loss = damage / denominator;
            if (unbreakingPresent) {
                // 舍入职责全部交给 ItemStack#hurt 的 floor，避免双重舍入
                return (float) loss;
            }
            return (float) Math.max(1.0, Math.floor(loss));
        }else{
            return (damage / 4.0F);
        }
    }

    /**
     * Gets the armor toughness provided by a single armor piece via its attribute modifiers.<br>
     *
     * @param stack The armor item stack.
     * @param slot  The equipment slot the piece is worn in.
     * @return The sum of the piece's {@link Attributes#ARMOR_TOUGHNESS} modifiers for that slot.
     * For vanilla armor: leather/iron/gold = 0, diamond = 2, netherite = 3.
     */
    public static float getPieceToughness(ItemStack stack, EquipmentSlot slot) {
        float toughness = 0.0F;
        for (AttributeModifier modifier : stack.getItem().getAttributeModifiers(slot, stack)
                .get(Attributes.ARMOR_TOUGHNESS)) {
            toughness += (float) modifier.getAmount();
        }
        return toughness;
    }
}
