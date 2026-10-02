package dev.newarmorsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import dev.newarmorsystem.api.CompatRegistration;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.ItemMassConfig;
import dev.newarmorsystem.api.MaterialMassConfig;
import dev.newarmorsystem.api.ModAttributes;
import dev.newarmorsystem.api.PlayerMassEffects;
import dev.newarmorsystem.api.TrimMass;
import dev.newarmorsystem.compat.CompatModules;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(NewArmorSystem.MOD_ID)
public class NewArmorSystem
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "new_armor_system";
    public NewArmorSystem(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        //region ModEventBus


        //end region

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        // 自定义属性（摔落伤害倍率 fall_damage_multiplier，与 NeoForge 1.21.1 同名属性同语义）：
        // 注册表 + 挂到玩家实体类型；随后由 FallDamage 的公式读取
        ModAttributes.ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(ModAttributes::onEntityAttributeModification);

        // 配置重载：重建质量系统的配置层（item_mass.entries 的修改即时生效，含删条目恢复原登记；
        // 事件对所有模组的配置都会派发，故需比对 spec 过滤）
        modEventBus.addListener(ItemMassConfig::onConfigReload);

        // 配置重载：一并重建盔甲纹饰系数表（armor_mass_coefficient.trim_coefficients）
        modEventBus.addListener(TrimMass::onConfigReload);

        // 配置重载：一并重建材料级质量系数表（armor_mass_coefficient.material_coefficients）
        modEventBus.addListener(MaterialMassConfig::onConfigReload);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(PlayerMassEffects.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // 原版工具绑定示例：按 Item 把原版 25 件工具全部纳入工具耐久法则
        // （铁/金/钻石/下界合金绑定各自护甲材料共享 B；木/石显式登记配置基数）。
        // 工具耐久公式开启时生效，默认关闭无影响。
        CompatRegistration.registerVanillaTools();

        // 物品级质量（配置文件版接入质量系统）：本阶段物品注册表已填充、配置已加载，
        // 是解析 "<物品 ID>=<质量>" 并重建配置层的安全时机（放在 commonSetup 而非构造期，
        // 正是为了不与注册时序产生依赖）。
        ItemMassConfig.apply();

        // 盔甲纹饰材料系数（armor_mass_coefficient.trim_coefficients）：只解析 "ID=系数" 字符串、
        // 不查注册表，故与物品质量同一时机重建；配置重载时由 TrimMass::onConfigReload 重建。
        TrimMass.applyConfig();

        // 材料级质量系数（armor_mass_coefficient.material_coefficients）：把"某个护甲材料多重"
        // 从代码登记搬到配置文件（模组材料不再需要改代码）；只比对字符串、不查注册表，
        // 故同样与物品质量同一时机重建，配置重载时由 MaterialMassConfig::onConfigReload 重建。
        MaterialMassConfig.apply();

        // 兼容模块（由原 NAS-Compatibility-Patch 合并进来）：为第三方模组护甲 / 工具导入
        // 护甲类型、耐久基数、质量系数、修理系数与工具绑定；其中的护甲值 / 盔甲韧性改写
        // 属于整合包内容，受 feature_toggles.experimentalFeaturesEnabled 门控（默认关闭）。
        // 走 enqueueWork：FMLCommonSetupEvent 的监听器并行执行，而这些登记表非线程安全，
        // 需在本阶段结束后回到主线程执行。
        event.enqueueWork(CompatModules::registerAll);
    }
}
