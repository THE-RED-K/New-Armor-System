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
import dev.newarmorsystem.api.PlayerMassEffects;

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
    }
}
