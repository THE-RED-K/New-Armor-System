package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.ArmorMaterialRules;
import dev.newarmorsystem.api.Config;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 自定义护甲材料（其他模组实现 {@link ArmorMaterial} 接口、不在原版 {@link ArmorMaterials} 枚举内）
 * 的耐久适配。
 *
 * <p>原版 {@code ArmorItem.<init>} 在构造时快照 {@code pMaterial.getDurabilityForType(type)} 作为基础耐久，
 * 自定义材料走 mod 自己的实现，既不读本模组改写的原版部位系数表，也不套用耐久公式。
 * <p>
 * 本注入在构造点拦截该调用：
 * <ul>
 *     <li>内置材料：原样放行（{@link ArmorMaterialsMixin} 已在 {@code getDurabilityForType} 内套用新公式）；</li>
 *     <li>自定义材料：经 {@link ArmorMaterialRules#effectiveDurabilityBase} 反推其耐久基数，
 *     套用 新部位系数 × 分类ArmorTypeCoefficient（未登记默认中甲，可通过 {@link ArmorClass#register} 覆盖）。</li>
 * </ul>
 *
 * @author THEREDK
 * @reason 自定义护甲材料纳入耐久公式
 */
@Mixin(ArmorItem.class)
public abstract class ArmorItemMixin {

    /**
     * 重定向 {@code ArmorItem.<init>} 内的 {@code ArmorMaterial#getDurabilityForType} 调用。
     *
     * <p>对自定义材料：{@code 基数 = getDurabilityForType(type) / 原版部位系数}，
     * 套用 新部位系数 × ArmorTypeCoefficient。
     * 若 mod 使用原版部位系数表，结果恰为 基数 × 新部位系数 × ArmorTypeCoefficient（中甲默认 1.0）。
     */
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ArmorMaterial;getDurabilityForType(Lnet/minecraft/world/item/ArmorItem$Type;)I"))
    private static int newArmorSystem$applyCustomMaterialDurability(ArmorMaterial material, ArmorItem.Type type) {
        if (material instanceof ArmorMaterials) {
            return material.getDurabilityForType(type); // 内置材料：ArmorMaterialsMixin 已套用新公式
        }

        // 自定义材料：反推耐久基数，套用新部位系数 × 分类ArmorTypeCoefficient
        int base = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.slotFactorBase.get() : 8;
        int newSlotFactor = switch (type) {            // 本模组新部位系数表
            case HELMET -> base + 5;
            case CHESTPLATE -> base + 8;
            case LEGGINGS -> base + 7;
            case BOOTS -> base + 4;
            default -> base + 8;
        };
        ArmorClass clazz = ArmorClass.of(material);    // 未登记默认中甲
        double result = ArmorMaterialRules.effectiveDurabilityBase(material, type) * newSlotFactor * clazz.ArmorTypeCoefficient();
        return Math.max(1, (int) Math.round(result));
    }
}
