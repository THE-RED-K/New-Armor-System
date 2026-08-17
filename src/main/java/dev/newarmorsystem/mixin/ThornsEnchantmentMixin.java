package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.MixinPriorities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 重做荆棘附魔（Thorns）：移除原版机制，并入本模组反伤系统。
 *
 * <p>原版 {@code ThornsEnchantment.doPostHurt}：按概率（{@code 0.15 × 等级}）触发反伤
 * （{@code 1 ~ 4} 点伤害），且触发时对随机一件荆棘护甲<b>额外损耗 2 点耐久</b>。
 *
 * <p>本模组：反伤在护甲<b>损失耐久时</b>统一结算
 * （{@link InventoryMixin#hurtArmor}），反伤比例 = 材料反伤比例 + 荆棘等级 × 60%，
 * <b>必定反伤</b>（无概率判定）。故原版机制整体禁用 —— {@code doPostHurt} 改为空实现。
 *
 * @author THEREDK
 * @reason 原版荆棘概率反伤与额外耐久损耗并入模组反伤系统
 */
// 接管型 mixin：低优先级先写入方法体，使其它模组的注入仍能落在接管后的方法上（机制与取值见 MixinPriorities）
@Mixin(value = net.minecraft.world.item.enchantment.ThornsEnchantment.class, priority = MixinPriorities.TAKEOVER)
public abstract class ThornsEnchantmentMixin {

    /**
     * 原版荆棘机制（概率反伤 + 额外耐久损耗）已被 {@link InventoryMixin#hurtArmor}
     * 中按比例（材料比例 + 每级 60% 荆棘等级）的反伤取代，此处改为空实现以禁用原版机制。
     * 原版触发链（近战 / 箭 / 三叉戟命中时 {@code EnchantmentHelper.doPostHurtEffects}）
     * 仍会调用本方法，但不再产生任何效果。
     *
     * @author THEREDK
     * @reason 原版荆棘概率反伤与额外耐久损耗并入模组反伤系统
     */
    @Overwrite
    public void doPostHurt(LivingEntity pUser, Entity pTarget, int pLevel) {
    }
}
