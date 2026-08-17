package dev.newarmorsystem.mixin;


import com.mojang.logging.LogUtils;
import dev.newarmorsystem.api.ArmorMaterialRules;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.ItemDurabilityRules;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TieredItem;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 工具耐久公式（默认关闭）：
 * <pre>
 *   工具耐久 = ( 护甲耐久基数 ) ^ 指数 × 修正系数
 * </pre>
 *
 * <p>耐久基数来源（与护甲体系同源，见 {@link dev.newarmorsystem.api.ArmorMaterialRules}），
 * 按 <b>Item 级 &gt; 保持原版</b> 分流，工具自身 {@code getUses()}
 * <b>永不参与</b>：
 * <ul>
 *   <li><b>Item 级（唯一绑定入口，精确到单把工具）</b>：
 *       {@link ItemDurabilityRules#registerToolArmorMaterial} 绑定单把工具到护甲材料后，
 *       基数 = {@link ArmorMaterialRules#effectiveDurabilityBase} —— 与护甲共享同一个 B
 *       （玩家调护甲基数时工具同步联动）；也可用
 *       {@link ItemDurabilityRules#registerToolDurabilityBase} 直接登记数值（最强控制）；
 *       系数用 {@link ItemDurabilityRules#registerToolCoefficient}。原版工具即按此法
 *       绑定（见 {@link dev.newarmorsystem.api.CompatRegistration#registerVanillaTools()}）。</li>
 *   <li><b>保持原版</b>：其余无登记的物品维持模组自设耐久；
 *       强制模式（{@code toolDurabilityForceAllMaterials}，默认关闭）开启时，
 *       此类工具会打一次 warn 提示未纳入法则。</li>
 * </ul>
 *
 * <p><b>不提供 Tier 级绑定</b>：Tier 是挖掘/工具等级，会被大量模组复用，按 Tier 绑定
 * 会波及所有复用该等级的物品；只按 {@link Item} 精确绑定，第三方工具完全不受影响。
 *
 * <p>例外（仍为单独设定耐久值，不参与公式）：剪刀、钓鱼竿、打火石等
 * 非 {@link TieredItem} 物品 —— 它们的耐久写死在各自构造器，天然排除。
 *
 * <p>注入点选在 {@link Item#getMaxDamage()}：{@code ItemStack.getMaxDamage()}、
 * 耐久条渲染（{@code getBarWidth}）、tooltip 显示、损坏判定等一切上层路径
 * 最终都汇聚于此，覆盖它即可全链路生效。
 *
 * @author THEREDK
 * @reason 工具耐久改为基于护甲耐久基数的指数公式（可配置，默认关闭）
 */
@Mixin(Item.class)
public abstract class ItemMixin {

    private static final Logger NEW_ARMOR_SYSTEM$LOGGER = LogUtils.getLogger();

    /** 强制模式下已 warn 过的 Item（去重，避免刷屏）。 */
    private static final Set<Item> NEW_ARMOR_SYSTEM$WARNED_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * 覆盖 {@link Item#getMaxDamage()}：
     * 总开关关闭时（默认）保持原版行为；
     * 开启时仅对 {@link TieredItem}（镐/斧/锹/锄/剑）按
     * {@code (护甲耐久基数)^指数 × 修正系数} 重算耐久并短路返回。
     *
     * @author THEREDK
     * @reason 工具耐久公式（默认关闭）
     */
    @Inject(method = "getMaxDamage", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$toolDurabilityFormula(CallbackInfoReturnable<Integer> cir) {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.COMMON.toolDurabilityFormulaEnabled.get()) {
            return; // 总开关默认关闭：保持原版工具耐久
        }
        Item item = (Item) (Object) this;
        if (!(item instanceof TieredItem)) {
            return; // 剪刀、钓鱼竿等非 TieredItem：仍为单独设定耐久值（例外）
        }
        Double base = newArmorSystem$durabilityBaseOf(item);
        if (base == null) {
            return; // 无登记：保持原版耐久
        }
        double coefficient = newArmorSystem$coefficientOf(item);
        double durability = Math.pow(base, Config.COMMON.toolDurabilityExponent.get()) * coefficient;
        // 指数过低（如 0.0）时结果可能 < 1，下限保护为 1（避免不可损坏的 0 耐久物品）
        cir.setReturnValue(Math.max(1, (int) Math.round(durability)));
    }

    /**
     * 工具耐久基数（<b>仅</b>来自登记，工具自身 {@code getUses()} 不参与），
     * 优先级：Item 显式登记 &gt; Item 护甲反推 &gt; {@code null}（保持原版）。
     *
     * <p>只认 Item 级绑定（精确到单把工具）：Tier 是挖掘/工具等级，会被大量模组复用，
     * 按 Tier 绑定会波及该等级的全部物品；需要精确控制时用
     * {@link ItemDurabilityRules#registerToolArmorMaterial} 只绑这一把。
     */
    private static Double newArmorSystem$durabilityBaseOf(Item item) {
        // 1. Item 级显式登记（最强控制）
        Double registered = ItemDurabilityRules.durabilityBaseOf(item);
        if (registered != null) {
            return registered;
        }
        // 2. Item 级护甲反推（与护甲共享同一个 B）
        for (ArmorMaterial material : ItemDurabilityRules.armorMaterialsOf(item)) {
            double base = ArmorMaterialRules.effectiveDurabilityBase(material, ArmorItem.Type.CHESTPLATE);
            if (base > 0) {
                return base;
            }
        }
        // 3. 无登记：保持原版
        if (Config.COMMON.toolDurabilityForceAllMaterials.get()) {
            newArmorSystem$warnUncoveredTool(item);
        }
        return null;
    }

    /**
     * 强制模式下，对无法纳入法则（无 Item 登记）的工具打一次 warn。
     */
    private static void newArmorSystem$warnUncoveredTool(Item item) {
        if (NEW_ARMOR_SYSTEM$WARNED_ITEMS.add(item)) {
            NEW_ARMOR_SYSTEM$LOGGER.warn(
                    "[NewArmorSystem] Item {} has no durability registration, cannot derive its durability base from armor. "
                            + "Keeping vanilla durability. Bind it via ItemDurabilityRules.registerToolArmorMaterial(Item, ArmorMaterial) "
                            + "or registerToolDurabilityBase(Item, double) to include it in the tool durability formula.",
                    item);
        }
    }

    /**
     * 工具耐久修正系数，优先级：Item 显式登记 &gt; 默认 1.0
     * （原版石工具的 0.25 由 {@link ItemDurabilityRules#registerToolCoefficient} 显式登记）。
     */
    private static double newArmorSystem$coefficientOf(Item item) {
        Double registered = ItemDurabilityRules.coefficientOf(item);
        return registered != null ? registered : 1.0;
    }
}
