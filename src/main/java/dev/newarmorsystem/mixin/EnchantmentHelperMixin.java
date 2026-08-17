package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.BrokenState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 损坏状态：broken 物品的附魔全部失效。
 *
 * <p>附魔查询链路（Forge patch 版 1.20.1）：
 * {@code EnchantmentHelper.getItemEnchantmentLevel} → {@code ItemStack.getEnchantmentLevel}
 * → {@code Item.getEnchantmentLevel} → {@link EnchantmentHelper#getTagEnchantmentLevel}（最终读 NBT 点）。
 * 拦截底层 {@code getTagEnchantmentLevel} 即可覆盖全部玩法逻辑；
 * {@code getItemEnchantmentLevel}（deprecated 入口）为防御性拦截。
 * 注意：不拦截 {@code getEnchantments} —— tooltip 附魔名（{@code getEnchantmentTags}）与
 * 物品附魔光效（{@code hasFoil}→{@code isEnchanted}）均直读 NBT、不经过它；
 * broken 物品保留附魔视觉，仅玩法效果失效。
 *
 * <p>{@code getTagEnchantmentLevel} 是 Forge patch 新增方法（非原版），无 obfuscation 映射，
 * 注入必须使用 {@code remap = false}（该方法在 dev 与 prod 中同名，不受 reobf 影响）。
 *
 * @author THEREDK
 * @reason broken 物品失去附魔效果
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getTagEnchantmentLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void newArmorSystem$disableBrokenEnchants(Enchantment pEnchantment, ItemStack pStack, CallbackInfoReturnable<Integer> cir) {
        if (BrokenState.isBroken(pStack)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void newArmorSystem$disableBrokenEnchantsDeprecated(Enchantment pEnchantment, ItemStack pStack, CallbackInfoReturnable<Integer> cir) {
        if (BrokenState.isBroken(pStack)) {
            cir.setReturnValue(0);
        }
    }
}
