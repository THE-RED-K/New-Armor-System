package dev.newarmorsystem.mixin;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import dev.newarmorsystem.api.ArmorAttributeRules;
import dev.newarmorsystem.api.BrokenState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 护甲属性（护甲值 / 盔甲韧性）覆写注入 —— 见 {@link ArmorAttributeRules}。
 *
 * <p>注入点选在 {@code ItemStack.getAttributeModifiers(EquipmentSlot)} 的 RETURN，
 * 而非 {@code ArmorItem.getAttributeModifiers}：
 * <ul>
 *   <li>该方法是属性面板、玩家属性计算（减伤公式等）与 tooltip 显示<b>共用</b>
 *       的数据源，在此统一改写可保证显示值与实际效果一致（原版/模组护甲通用）；</li>
 *   <li>覆盖所有护甲实现路径：{@link ArmorItem} 子类、通过 Forge
 *       {@code ItemAttributeModifierEvent} 追加属性、以及任何在 map 中提供
 *       ARMOR / ARMOR_TOUGHNESS 条目的自定义物品。</li>
 * </ul>
 *
 * <p>改写规则（接管语义）：
 * <ul>
 *   <li>登记值 {@code armor} / {@code toughness} 非 0：替换 map 中对应属性的全部条目
 *       （保留首个条目 UUID，无条目时回退原版标准 UUID {@code ArmorItem.ARMOR_MODIFIER_UUID}
 *       / {@code ARMOR_TOUGHNESS_MODIFIER_UUID}），其余属性条目原样保留；</li>
 *   <li>登记值为 0：移除对应属性条目（不再提供该属性，tooltip 不显示 +0 行）；</li>
 *   <li>物品在该槽位<b>不提供</b>护甲属性（非 {@link ArmorItem} 的装备槽，
 *       且 map 中无 ARMOR / ARMOR_TOUGHNESS 条目）时直接跳过，不添加新条目；
 *       仅当通过 {@link ArmorAttributeRules#register(Item, EquipmentSlot, double, double)}
 *       做了<b>强制登记</b>时才无条件写入（适用于非 {@link ArmorItem} 的自定义穿戴物）；</li>
 *   <li>broken 物品不参与改写：由 {@link ItemStackMixin} 统一清空全部修饰符，
 *       此处显式检查避免注入顺序差异导致 broken 护甲被重新赋予属性。</li>
 * </ul>
 *
 * @author THEREDK
 * @reason 护甲值/盔甲韧性全局覆写（附属模组 API 的注入端）
 */
@Mixin(ItemStack.class)
public abstract class ArmorAttributeMixin {

    /**
     * 原版护甲属性修饰符的标准 UUID（{@code ArmorItem} 中为 private 字段，
     * 1.20.1 映射下无法直接访问，故按原版公开值硬编码；Mojang 从未变更这两个 UUID）。
     * 护甲值：{@code 556e1665-4b1f-43f6-a3c0-14dbd68e0b6a}；
     * 盔甲韧性：{@code 2ad3f246-fee1-4e67-b886-69b380efd156}。
     */
    @Unique
    private static final UUID NEW_ARMOR_SYSTEM$ARMOR_MODIFIER_UUID =
            UUID.fromString("556e1665-4b1f-43f6-a3c0-14dbd68e0b6a");
    @Unique
    private static final UUID NEW_ARMOR_SYSTEM$ARMOR_TOUGHNESS_MODIFIER_UUID =
            UUID.fromString("2ad3f246-fee1-4e67-b886-69b380efd156");

    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$applyArmorAttributeRules(EquipmentSlot pSlot,
                                                         CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        // broken 物品由 ItemStackMixin 统一清空修饰符；此处独立检查，与注入顺序无关
        if (BrokenState.isBroken(stack)) {
            return;
        }
        Item item = stack.getItem();
        ArmorAttributeRules.ArmorStats stats = ArmorAttributeRules.statsOf(item, pSlot);
        if (stats == null) {
            return;
        }
        Multimap<Attribute, AttributeModifier> map = cir.getReturnValue();
        boolean hasArmor = map.containsKey(Attributes.ARMOR);
        boolean hasToughness = map.containsKey(Attributes.ARMOR_TOUGHNESS);
        boolean isArmorInSlot = item instanceof ArmorItem armorItem && armorItem.getEquipmentSlot() == pSlot;
        // 强制登记（物品 × 槽位）无视生效条件；否则仅当该物品在该槽位原本提供护甲属性才改写
        boolean forced = ArmorAttributeRules.isForced(item, pSlot);
        if (!forced && !hasArmor && !hasToughness && !isArmorInSlot) {
            return;  // 该物品在该槽位不提供护甲属性
        }

        // 移除旧护甲属性条目，其余属性（击退抗性、模组自定义属性等）原样保留
        LinkedHashMultimap<Attribute, AttributeModifier> rewritten = LinkedHashMultimap.create();
        map.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ARMOR && attribute != Attributes.ARMOR_TOUGHNESS) {
                rewritten.put(attribute, modifier);
            }
        });
        if (stats.armor() != 0.0) {
            rewritten.put(Attributes.ARMOR, new AttributeModifier(
                    newArmorSystem$firstModifierUuid(map, Attributes.ARMOR, NEW_ARMOR_SYSTEM$ARMOR_MODIFIER_UUID),
                    "Armor modifier", stats.armor(), AttributeModifier.Operation.ADDITION));
        }
        if (stats.toughness() != 0.0) {
            rewritten.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                    newArmorSystem$firstModifierUuid(map, Attributes.ARMOR_TOUGHNESS, NEW_ARMOR_SYSTEM$ARMOR_TOUGHNESS_MODIFIER_UUID),
                    "Armor toughness", stats.toughness(), AttributeModifier.Operation.ADDITION));
        }
        cir.setReturnValue(rewritten);
    }

    /**
     * 返回 map 中指定属性首个修饰符的 UUID；该属性无条目时返回 {@code fallback}
     * （原版标准 UUID），保证返回值永不为 null。
     */
    @Unique
    private static UUID newArmorSystem$firstModifierUuid(Multimap<Attribute, AttributeModifier> map, Attribute attribute,
                                                         UUID fallback) {
        for (AttributeModifier modifier : map.get(attribute)) {
            return modifier.getId();
        }
        return fallback;
    }
}
