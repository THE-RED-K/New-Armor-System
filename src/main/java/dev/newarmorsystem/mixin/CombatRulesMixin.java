package dev.newarmorsystem.mixin;


import dev.newarmorsystem.api.ArmorReduceEvent;
import dev.newarmorsystem.api.MixinPriorities;
import dev.newarmorsystem.api.NewCombatRules;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 注意：本模组使用 {@code @Overwrite} 直接覆盖原方法体，
 * 因此与其它同样修改护甲减伤公式的 Mod 不兼容 —— 这是本模组的设计定位。
 * <p>
 * 不要在此处添加 {@code remap = false}，否则发布 jar（运行时方法名为 SRG 名）会失效。
 *
 * @see NewCombatRules#getDamageAfterArmor(float, float, float)
 */
// 接管型 mixin：低优先级先写入方法体，使其它模组的注入仍能落在接管后的方法上（机制与取值见 MixinPriorities）
@Mixin(value = CombatRules.class, priority = MixinPriorities.TAKEOVER)
public abstract class CombatRulesMixin {
    /**
     * 用新护甲减伤公式完全替换原版的 {@link CombatRules#getDamageAfterAbsorb(float, float, float)}，
     * 公式结算完成后派发 {@link ArmorReduceEvent}（监听者可改写结算后伤害；
     * 无监听者时行为与不派发完全一致）。
     *
     * @author THEREDK
     * @reason 用新护甲减伤公式完全替换原版公式，并开放结果出口
     * @param damage    The amount of damage.
     * @param armor     The amount of armor.
     * @param toughness The amount of toughness.
     * @return The amount of damage after armor reduction.
     */
    @Overwrite
    public static float getDamageAfterAbsorb(float damage, float armor, float toughness) {
        float reduced = NewCombatRules.getDamageAfterArmor(damage, armor, toughness);
        // 派发护甲减伤结算事件：静态调用点没有受击实体上下文，事件不带实体
        // （需要实体的监听者请配合 Forge 原生 LivingHurtEvent / LivingDamageEvent）。
        ArmorReduceEvent event = new ArmorReduceEvent(damage, armor, toughness, reduced);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getNewDamage();
    }
}
