package dev.newarmorsystem.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.List;

/**
 * 护甲耐久体系装配 —— 把「部位系数 × 材料耐久基数 × 护甲类型系数」写入每件护甲的
 * {@code DataComponents.MAX_DAMAGE}。
 *
 * <p><b>为什么不再用 Mixin（1.20.1 → 1.21.1 的最大重构）</b>：
 * 1.20.1 的耐久由 {@code ArmorMaterial#getDurabilityForType} 在 {@code ArmorItem}
 * 构造时快照，故模组用两个 Mixin 分别改写枚举材料表（{@code ArmorMaterialsMixin}）
 * 与自定义材料构造点（{@code ArmorItemMixin}）。1.21 起：
 * <ul>
 *   <li>{@code ArmorMaterial} 成为 <b>record</b>，<b>不再包含耐久字段</b>；</li>
 *   <li>护甲耐久改由物品的 {@code DataComponents.MAX_DAMAGE} 承载；</li>
 *   <li>NeoForge 提供 {@link ModifyDefaultComponentsEvent}，可在注册期统一改写任意物品的默认组件。</li>
 * </ul>
 * 因此本模块改为「事件驱动、注册期写入」：不再需要任何 Mixin，且<b>天然覆盖第三方模组
 * 注册的护甲</b>（原方案对非 {@code ArmorItem} 子类需另外处理）。
 *
 * <p><b>反推规则</b>：内置材料走 {@link ArmorMaterialRules#durabilityBase}（可配置）；
 * 其余材料（1.21 新增的 {@code ARMADILLO}、第三方自定义材料）按物品<b>当前原版耐久</b>
 * 除以 <b>原版部位系数</b> 反推基数，再套用新公式 —— 若该材料原本就用原版部位系数表，
 * 结果恰为「基数 × 新部位系数 × AT」，与原 1.20.1 的 {@code ArmorItemMixin} 行为一致。
 *
 * @author THEREDK
 * @reason 新护甲系统的耐久体系与配方/硬度对齐，属于设计定位，与原版数值冲突为预期。
 */
public final class ArmorDurability {

    private ArmorDurability() {
    }

    /**
     * 在默认组件阶段改写每件护甲的 {@code MAX_DAMAGE}。
     *
     * <p>需注册到<b>模组事件总线</b>（该事件实现 {@code IModBusEvent}）。
     *
     * @param event 默认组件修改事件
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        // 先取快照：事件回调会向内部映射写入，避免边遍历边修改
        List<Item> items = event.getAllItems().toList();
        for (Item item : items) {
            if (!(item instanceof ArmorItem armor)) {
                continue;                       // 非护甲不介入
            }
            Integer vanillaMaxDamage = item.components().get(DataComponents.MAX_DAMAGE);
            if (vanillaMaxDamage == null || vanillaMaxDamage <= 0) {
                continue;                       // 无耐久（不可损坏）的护甲保持原样
            }
            ArmorItem.Type type = armor.getType();
            ArmorMaterial material = armor.getMaterial().value();
            double base = ArmorMaterialRules.resolveDurabilityBase(material, type, vanillaMaxDamage);
            if (base <= 0) {
                continue;                       // 兜底：无法确定基数时保持原版
            }
            ArmorClass clazz = ArmorClass.of(material);
            // 舍入与 1.20.1 保持一致：内置材料路径为「截断」而非四舍五入
            // （1.20.1 的 ArmorMaterialsMixin 为 (int) 强转，ArmorItemMixin 才是 round；
            //  此处统一按截断，避免金护甲等非整数结果产生 1 点耐久差异：10×13×0.75 = 97.5 → 97）。
            // Math.max(1, ...) 仅为下限保护，在当前取值域（最小 8×12×0.75 = 72）下不会触发。
            int newMaxDamage = Math.max(1,
                    (int) (ArmorMaterialRules.slotFactor(type) * base * clazz.ArmorTypeCoefficient()));
            final int result = newMaxDamage;
            event.modify(item, builder -> builder.set(DataComponents.MAX_DAMAGE, result));
        }
    }
}
