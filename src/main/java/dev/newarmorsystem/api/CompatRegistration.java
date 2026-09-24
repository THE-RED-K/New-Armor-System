package dev.newarmorsystem.api;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

/**
 * 兼容性模组总登记接口 —— 一次性登记一个护甲材料的全部规则。
 *
 * <p>将第三方模组材料完整纳入本系统所需的五项登记合并为一次调用：
 * 护甲类型（{@link ArmorClass}）、耐久基数（{@link ArmorMaterialRules}）、
 * 材料质量系数（{@link PlayerMass}）、铁砧修理花费系数（{@link ArmorRepair}）、
 * 工具物品绑定（{@link ItemDurabilityRules}）：
 *
 * <pre>{@code
 * CompatRegistration.registerMaterialRulesForTools(
 *     myArmorMaterial,            // 第三方护甲材料（1.21：getMaterial().value() 取到的注册表实例）
 *     ArmorClass.MEDIUM,          // 护甲类型（null = 不登记，保持默认：内置走内置规则，自定义默认中甲）
 *     32.0,                       // 耐久基数（>0 登记；≤0 不登记，走内置/反推）
 *     1.2,                        // 材料质量系数（>0 登记；≤0 不登记，默认 1.0）
 *     1.5,                        // 铁砧修理花费系数（>0 登记；≤0 不登记，用全局配置）
 *     1.0,                        // 工具修正系数（>0 登记；≤0 不登记，默认 1.0）
 *     mySword, myPickaxe, ...);   // 绑定的具体工具物品（可为空数组）
 * }</pre>
 *
 * <p>绑定的工具（仅限 {@link Item}）会与该护甲材料<b>共享同一个耐久基数</b>：工具的
 * {@code D = B^指数 × 系数} 与护甲的 {@code D = B × 部位系数 × AT} 使用同一个 B，
 * 玩家在 Config 调整该材料的耐久基数时护甲与工具同步联动（耐久基数通用）。
 * 一个 Item 可绑定多个材料（一对多），工具取第一个可反推的材料（见
 * {@link ItemDurabilityRules#registerToolArmorMaterials}）。
 *
 * <p>工具修正系数与护甲绑定<b>正交</b>：有护甲材料的工具同样可配系数
 * （{@code toolCoefficient} 位或 {@link ItemDurabilityRules#registerToolCoefficient}），
 * 共享 B 的同时叠加系数 —— 无需为蹭系数而放弃护甲绑定改走显式基数登记。
 *
 * <p><b>只提供按 Item（具体工具）绑定</b>：Tier 是挖掘/工具等级，会被大量模组复用，
 * 按 Tier 绑定会波及该等级全部物品（几乎所有添加工具的模组都存在此复用），
 * 故不提供 Tier 级登记。原版工具即通过 {@link #registerVanillaTools()} 按 Item 绑定，
 * 同时充当使用示例。
 *
 * <p>各登记项分别等价于：{@link ArmorClass#register}、
 * {@link ArmorMaterialRules#registerDurabilityBase}、
 * {@link PlayerMass#registerMaterialMassCoefficient}、
 * {@link ArmorRepair#registerRepairCostCoefficient}、
 * {@link ItemDurabilityRules#registerToolArmorMaterial}（每个 Item 单值绑定，
 * 系数叠加等价于 {@link ItemDurabilityRules#registerToolCoefficient}）。
 * 登记表<b>非线程安全</b>，建议加载期（<b>构造期 / 注册期</b>）调用。
 *
 * <p><b>1.20.1 → 1.21.1</b>：材料由枚举变为注册表条目（{@code Holder<ArmorMaterial>}），
 * 故原版常量需 {@code .value()} 取实例（见 {@link #registerVanillaTools()}）；
 * 附魔/材料相关的注入接口签名同步调整。
 *
 * @author THEREDK
 */
public final class CompatRegistration {

    private CompatRegistration() {
    }

    /**
     * 按<b>具体工具物品</b>的总登记接口（唯一登记入口）。
     *
     * <p>工具绑定使用 {@link ItemDurabilityRules#registerToolArmorMaterial}（Item 级，
     * 精确到单把工具）。Tier 是挖掘/工具等级，会被大量模组复用；按 Item 绑定只影响
     * 列出的这几把工具，不波及同等级的其他物品（第三方复用该 Tier 的工具不受影响）。
     *
     * <p>{@code toolCoefficient} 让有护甲材料的工具在<b>共享护甲 B</b> 的同时叠加
     * 修正系数（{@code D = B^指数 × 系数}），无需两步调用或改走显式基数登记。
     *
     * <p>本接口所有数值参数均以 <b>&gt; 0 表示登记、≤ 0 表示不登记该维度</b>
     * （各维度回退到各自的默认/全局值）。因此想<b>局部免费修</b>（系数 0）时
     * 无法经本接口表达——请单独调用
     * {@link ArmorRepair#registerRepairCostCoefficient(ArmorMaterial, Double)}
     * 传 {@code 0.0}（或全局配置 {@code anvilRepairCostCoefficient} 设 0）。
     *
     * @param material                护甲材料（{@code ArmorMaterials.LEATHER.value()} 等注册表实例或自定义材料）
     * @param armorClass              护甲类型（{@code null} = 不登记，保持默认）
     * @param durabilityBase          耐久基数（> 0 登记；≤ 0 不登记，走内置/反推）
     * @param materialMassCoefficient 材料质量系数（> 0 登记；≤ 0 不登记，默认 1.0）
     * @param repairCostCoefficient   铁砧修理花费系数（> 0 登记；≤ 0 不登记，用全局配置；局部免费见上方说明）
     * @param toolCoefficient         工具修正系数（> 0 登记；≤ 0 不登记，默认 1.0）
     * @param toolItems               绑定到该材料的具体工具物品（可为空数组）
     */
    @SuppressWarnings("unused") // 公开 API：总登记入口，供第三方模组/整合包调用，本模组仅提供示例
    public static void registerMaterialRulesForTools(
            ArmorMaterial material,
            @Nullable ArmorClass armorClass,
            double durabilityBase,
            double materialMassCoefficient,
            double repairCostCoefficient,
            double toolCoefficient,
            Item... toolItems) {
        if (armorClass != null) {
            ArmorClass.register(material, armorClass);
        }
        if (durabilityBase > 0) {
            ArmorMaterialRules.registerDurabilityBase(material, durabilityBase);
        }
        if (materialMassCoefficient > 0) {
            PlayerMass.registerMaterialMassCoefficient(material, materialMassCoefficient);
        }
        if (repairCostCoefficient > 0) {
            ArmorRepair.registerRepairCostCoefficient(material, repairCostCoefficient);
        }
        if (toolItems != null) {
            for (Item tool : toolItems) {
                if (tool != null) {
                    ItemDurabilityRules.registerToolArmorMaterial(tool, material, toolCoefficient);
                }
            }
        }
    }

    /**
     * 便捷入口：按<b>材料 × 部位</b>登记护甲属性（护甲值 / 盔甲韧性）覆写。
     * 等价于 {@link ArmorAttributeRules#register(ArmorMaterial, ArmorItem.Type, double, double)}。
     *
     * <p>自动应用到该材料下全部护甲物品的对应部位，各部位可配不同值；
     * 未登记的部位保持原版值。登记后属性面板、玩家实际减伤与 tooltip 显示
     * 均为新值（显示与效果天然一致，见 {@link ArmorAttributeHandler}）。
     *
     * @param material  护甲材料（注册表实例或自定义材料）
     * @param type      部位（头盔 / 胸甲 / 护腿 / 靴子）
     * @param armor     护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    @SuppressWarnings("unused") // 公开 API：总登记入口，供第三方模组/整合包调用
    public static void registerArmorAttributes(ArmorMaterial material, ArmorItem.Type type, double armor, double toughness) {
        ArmorAttributeRules.register(material, type, armor, toughness);
    }

    /**
     * 便捷入口：按<b>具体物品</b>登记护甲属性（护甲值 / 盔甲韧性）覆写。
     * 等价于 {@link ArmorAttributeRules#register(Item, double, double)}。
     *
     * <p>精确到单件护甲（如只改铁头盔不改铁胸甲），优先级高于材料表登记。
     *
     * @param item      护甲物品（原版/模组均可）
     * @param armor     护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    @SuppressWarnings("unused") // 公开 API：总登记入口，供第三方模组/整合包调用
    public static void registerArmorAttributes(Item item, double armor, double toughness) {
        ArmorAttributeRules.register(item, armor, toughness);
    }

    /**
     * 便捷入口：按<b>物品 × 槽位（强制）</b>登记护甲属性（护甲值 / 盔甲韧性）覆写。
     * 等价于 {@link ArmorAttributeRules#register(Item, EquipmentSlot, double, double)}。
     *
     * <p><b>用于非 {@code ArmorItem} 物品</b>（自定义穿戴物、饰品等）：显式指定槽位后
     * 无条件在该槽位写入护甲值/韧性，即使该物品原本不提供任何护甲属性条目。
     * 优先级最高，覆盖按物品与按材料登记。
     *
     * @param item      任意物品（原版/模组、护甲/非护甲均可）
     * @param slot      生效的装备槽位（如 {@code EquipmentSlot.HEAD}、{@code EquipmentSlot.CHEST}）
     * @param armor     护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    @SuppressWarnings("unused") // 公开 API：总登记入口，供第三方模组/整合包调用
    public static void registerArmorAttributes(Item item, EquipmentSlot slot, double armor, double toughness) {
        ArmorAttributeRules.register(item, slot, armor, toughness);
    }

    /** 原版工具绑定是否已执行（幂等保护：装配阶段与外部调用都只会真正执行一次）。 */
    private static boolean vanillaToolsRegistered = false;

    /**
     * 原版工具绑定示例 —— 按 Item 把原版 25 件工具全部纳入工具耐久法则。
     *
     * <p>铁 / 金 / 钻石 / 下界合金工具绑定到各自护甲材料（{@link ItemDurabilityRules
     * #registerToolArmorMaterial} 的推荐用法）：绑定后这些工具与对应护甲
     * <b>共享同一个耐久基数</b>（铁剑等 5 件铁工具共用 {@code ArmorMaterials.IRON}
     * 的 B，下界合金 = 钻石 + 金/2），玩家调 {@code ironDurabilityBase} 等配置时护甲与
     * 工具同步联动。
     *
     * <p>木 / 石工具无对应护甲材料，改为显式登记各自配置基数
     * （{@code woodDurabilityBase} / {@code stoneDurabilityBase}）；石工具额外登记
     * {@code × 0.25} 修正系数（原"金式功能性特例"）。
     *
     * <p><b>调用时机（1.20.1 → 1.21.1）</b>：1.20.1 由本模组在
     * {@code FMLCommonSetupEvent} 调用；1.21.1 改由 {@link ToolDurability}
     * 在 {@code ModifyDefaultComponentsEvent} 装配阶段确保调用一次
     * （见该类对时序的说明）。本方法<b>幂等</b>，可安全重复调用。
     * 第三方模组可参照此写法用
     * {@link #registerMaterialRulesForTools} 一次登记完整规则。
     */
    public static void registerVanillaTools() {
        if (vanillaToolsRegistered) {
            return;
        }
        vanillaToolsRegistered = true;
        // 铁：原版五件铁工具全部绑定铁护甲材料（共享 B = ironDurabilityBase）
        registerToolGroup(ArmorMaterials.IRON.value(),
                Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE);
        // 金
        registerToolGroup(ArmorMaterials.GOLD.value(),
                Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE);
        // 钻石
        registerToolGroup(ArmorMaterials.DIAMOND.value(),
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE);
        // 下界合金（护甲侧 B = 钻石 + 金/2）
        registerToolGroup(ArmorMaterials.NETHERITE.value(),
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE);
        // 木：无护甲材料，显式登记 woodDurabilityBase（默认 7），系数默认 1.0
        registerToolGroup(Config.COMMON.woodDurabilityBase.get(), 1.0,
                Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE);
        // 石：无护甲材料，登记 stoneDurabilityBase（默认 24）+ 0.25 修正系数
        registerToolGroup(Config.COMMON.stoneDurabilityBase.get(), 0.25,
                Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE);
    }

    /** 把一组工具物品绑定到同一护甲材料（无系数：整组系数取默认 1.0）。 */
    private static void registerToolGroup(ArmorMaterial material, Item... tools) {
        for (Item tool : tools) {
            if (tool != null) {
                ItemDurabilityRules.registerToolArmorMaterial(tool, material);
            }
        }
    }

    /** 把一组工具物品绑定到同一护甲材料，并为整组统一登记修正系数（有系数的护甲绑定版）。 */
    @SuppressWarnings("unused") // 与无系数版对称的辅助重载：当前原版示例组系数均默认 1.0，留作未来需要整组统一系数时使用
    private static void registerToolGroup(ArmorMaterial material, double coefficient, Item... tools) {
        for (Item tool : tools) {
            if (tool != null) {
                ItemDurabilityRules.registerToolArmorMaterial(tool, material, coefficient);
            }
        }
    }

    /** 为一组无护甲材料的工具显式登记耐久基数与修正系数（原版木/石示例辅助）。 */
    private static void registerToolGroup(double durabilityBase, double coefficient, Item... tools) {
        for (Item tool : tools) {
            if (tool != null) {
                ItemDurabilityRules.registerToolDurabilityBase(tool, durabilityBase);
                if (coefficient != 1.0) {
                    ItemDurabilityRules.registerToolCoefficient(tool, coefficient);
                }
            }
        }
    }
}
