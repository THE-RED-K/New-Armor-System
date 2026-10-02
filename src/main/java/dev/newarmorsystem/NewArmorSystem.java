package dev.newarmorsystem;

import dev.newarmorsystem.api.ArmorAttributeHandler;
import dev.newarmorsystem.api.ArmorDurability;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.ItemMassConfig;
import dev.newarmorsystem.api.MaterialMassConfig;
import dev.newarmorsystem.api.PlayerMassEffects;
import dev.newarmorsystem.api.ToolDurability;
import dev.newarmorsystem.api.TrimMass;
import dev.newarmorsystem.compat.CompatModules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(NewArmorSystem.MOD_ID)
public class NewArmorSystem {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "new_armor_system";

    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public NewArmorSystem(IEventBus modEventBus, ModContainer modContainer) {
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        // 兼容模块（由原 NAS-Compatibility-Patch 合并进来）：为第三方模组护甲 / 工具导入护甲类型、
        // 耐久基数、质量系数、修理系数与工具绑定。**必须注册在 ArmorDurability / ToolDurability 之前**
        // —— 它们在本阶段用这些登记烘焙耐久；其中的护甲值 / 盔甲韧性改写属于整合包内容，
        // 受 feature_toggles.experimentalFeaturesEnabled 门控（默认关闭）。
        modEventBus.addListener(CompatModules::onModifyDefaultComponents);

        // 护甲耐久体系：注册期改写每件护甲的 MAX_DAMAGE（部位系数 × 材料耐久基数 × 护甲类型系数）
        modEventBus.addListener(ArmorDurability::onModifyDefaultComponents);

        // 工具耐久公式（默认关闭）：同一组件阶段改写 TieredItem 的 MAX_DAMAGE。
        // 必须注册在 ArmorDurability 之后 —— 自定义材料的耐久基数来自护甲侧的装配期反推缓存。
        modEventBus.addListener(ToolDurability::onModifyDefaultComponents);

        // 物品级质量（配置文件版接入质量系统）：本阶段「全部物品已注册 + 配置已加载」，
        // 是解析 item_mass.entries 并重建配置层的安全时机（NeoForge 1.21 的注册事件晚于
        // common setup，故不能用 FMLCommonSetupEvent 做物品 ID 解析，与 ToolDurability 同源）。
        modEventBus.addListener(ItemMassConfig::onModifyDefaultComponents);

        // 盔甲纹饰材料系数（armor_mass_coefficient.trim_coefficients）：与物品质量同一阶段解析
        // （该阶段配置必已加载），只解析 "ID=系数" 字符串、不查注册表。
        modEventBus.addListener(TrimMass::onModifyDefaultComponents);

        // 材料级质量系数（armor_mass_coefficient.material_coefficients）：同一阶段解析，
        // 把"某个护甲材料多重"从代码登记搬到配置文件（模组材料不再需要改代码）。
        modEventBus.addListener(MaterialMassConfig::onModifyDefaultComponents);

        // 配置重载：重建配置层（item_mass.entries 的修改即时生效，含删条目恢复原登记；
        // 事件对所有模组的配置都会派发，故需比对 spec 过滤）
        modEventBus.addListener(ItemMassConfig::onConfigReload);

        // 配置重载：一并重建盔甲纹饰系数表
        modEventBus.addListener(TrimMass::onConfigReload);

        // 配置重载：一并重建材料级质量系数表
        modEventBus.addListener(MaterialMassConfig::onConfigReload);

        // 质量系统的玩家效果：移速减益 / 击退抗性 / 重力联动（每 tick 派生）
        NeoForge.EVENT_BUS.register(PlayerMassEffects.class);

        // 护甲值 / 盔甲韧性覆写：在物品属性计算阶段接管 ARMOR / ARMOR_TOUGHNESS 条目
        NeoForge.EVENT_BUS.register(ArmorAttributeHandler.class);
    }
}
