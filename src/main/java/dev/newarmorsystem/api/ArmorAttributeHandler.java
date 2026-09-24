package dev.newarmorsystem.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 护甲属性覆写的注入端 —— 把 {@link ArmorAttributeRules} 的登记应用到物品属性上。
 *
 * <p><b>为什么不再用 Mixin（1.20.1 → 1.21.1 的重构）</b>：
 * 1.20.1 的注入端是 {@code @Mixin(ItemStack.class)} 注入
 * {@code ItemStack#getAttributeModifiers(EquipmentSlot)} 的 RETURN（返回可写的 {@code Multimap}）。
 * 1.21.1 该签名已不存在：
 * <ul>
 *   <li>护甲属性改由数据组件 {@code DataComponents.ATTRIBUTE_MODIFIERS} 承载；
 *       统一入口是 <b>无槽位参数</b>的 {@code ItemStack#getAttributeModifiers()}
 *       （返回 {@code ItemAttributeModifiers} record，条目自带 {@code EquipmentSlotGroup}），
 *       且它是 NeoForge 扩展接口 {@code IItemStackExtension} 的 default 方法；</li>
 *   <li>NeoForge 在该方法内派发原生 {@link ItemAttributeModifierEvent}，
 *       并提供 {@code removeAllModifiersFor} / {@code addModifier}，足以完整表达
 *       "整体接管 ARMOR / ARMOR_TOUGHNESS 条目"的语义。</li>
 * </ul>
 * 因此本模块改为事件驱动，<b>零 Mixin</b>。原 1.20.1 中「保留原条目 UUID」的约定，
 * 在 1.21 对应「保留原条目 {@link ResourceLocation} id」，无条目时回退原版标准 id
 * {@code minecraft:armor.<部位>}（原版 {@code ArmorItem} 即用此 id）。
 *
 * <p><b>生效条件</b>与 1.20.1 一致：
 * <ul>
 *   <li><b>强制登记</b>（物品 × 槽位）：无视下述条件，直接在该槽位写入/接管属性；</li>
 *   <li>登记物品为 {@link ArmorItem} 时，仅在其<b>对应装备槽位</b>生效；</li>
 *   <li>非 {@link ArmorItem} 物品：仅当该物品在目标槽位<b>原本就提供护甲值或韧性条目</b>
 *       才改写，需要无条件添加请用强制登记。</li>
 * </ul>
 *
 * <p>broken 物品不参与改写：由 {@code ItemStackMixin} 统一清空全部修饰符。
 *
 * @author THEREDK
 * @reason 护甲值/盔甲韧性全局覆写（附属模组 API 的注入端）
 */
public final class ArmorAttributeHandler {

    private ArmorAttributeHandler() {
    }

    /**
     * 在物品属性计算阶段接管 ARMOR / ARMOR_TOUGHNESS 条目。
     *
     * <p>需注册到 NeoForge 事件总线（该事件在 {@code ItemStack#getAttributeModifiers()} 内派发）。
     *
     * @param event 物品属性修饰符事件
     */
    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (BrokenState.isBroken(stack)) {
            return;  // broken 物品由 ItemStackMixin 统一清空修饰符
        }
        Item item = stack.getItem();

        // 1) 收集本次要接管的槽位（按 1.20.1 的生效条件筛选），并记下各自的生效属性
        EnumMap<EquipmentSlot, ArmorAttributeRules.ArmorStats> plan = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ArmorAttributeRules.ArmorStats stats = ArmorAttributeRules.statsOf(item, slot);
            if (stats == null) {
                continue;
            }
            if (!ArmorAttributeRules.isForced(item, slot)) {
                boolean isArmorInSlot = item instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
                if (!isArmorInSlot && !newArmorSystem$hasArmorEntry(event, slot)) {
                    continue;  // 该物品在该槽位不提供护甲属性
                }
            }
            plan.put(slot, stats);
        }
        if (plan.isEmpty()) {
            return;
        }

        // 2) 保留原条目 id（须在移除之前捕获）；无条目时回退原版标准 id：minecraft:armor.<部位>
        EnumMap<EquipmentSlot, ResourceLocation> ids = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : plan.keySet()) {
            ids.put(slot, newArmorSystem$firstArmorModifierId(event, slot));
        }

        // 3) 接管：移除 ARMOR / ARMOR_TOUGHNESS 全部条目，再按 plan 逐槽位写入
        event.removeAllModifiersFor(Attributes.ARMOR);
        event.removeAllModifiersFor(Attributes.ARMOR_TOUGHNESS);
        for (var entry : plan.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ArmorAttributeRules.ArmorStats stats = entry.getValue();
            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
            ResourceLocation id = ids.get(slot);
            if (stats.armor() != 0.0) {
                event.addModifier(Attributes.ARMOR,
                        new AttributeModifier(id, stats.armor(), AttributeModifier.Operation.ADD_VALUE), group);
            }
            if (stats.toughness() != 0.0) {
                event.addModifier(Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(id, stats.toughness(), AttributeModifier.Operation.ADD_VALUE), group);
            }
        }
    }

    /** 该槽位是否原本就提供护甲值/盔甲韧性条目。 */
    private static boolean newArmorSystem$hasArmorEntry(ItemAttributeModifierEvent event, EquipmentSlot slot) {
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            if (entry.slot().test(slot) && newArmorSystem$isArmorAttribute(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该槽位护甲属性的修饰符 id：优先取原有条目的 id（保留原 id）；
     * 无条目时回退原版标准 id {@code minecraft:armor.<部位>}（原版 {@code ArmorItem} 的写法）。
     */
    private static ResourceLocation newArmorSystem$firstArmorModifierId(ItemAttributeModifierEvent event, EquipmentSlot slot) {
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            if (entry.slot().test(slot) && newArmorSystem$isArmorAttribute(entry)) {
                return entry.modifier().id();
            }
        }
        return ResourceLocation.withDefaultNamespace("armor." + slot.getName());
    }

    private static boolean newArmorSystem$isArmorAttribute(ItemAttributeModifiers.Entry entry) {
        return entry.attribute().value() == Attributes.ARMOR.value()
                || entry.attribute().value() == Attributes.ARMOR_TOUGHNESS.value();
    }
}
