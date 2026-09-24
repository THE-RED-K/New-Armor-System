package dev.newarmorsystem;

import dev.newarmorsystem.api.ArmorAttributeHandler;
import dev.newarmorsystem.api.ArmorDurability;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.PlayerMassEffects;
import dev.newarmorsystem.api.ToolDurability;
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

        // 护甲耐久体系：注册期改写每件护甲的 MAX_DAMAGE（部位系数 × 材料耐久基数 × 护甲类型系数）
        modEventBus.addListener(ArmorDurability::onModifyDefaultComponents);

        // 工具耐久公式（默认关闭）：同一组件阶段改写 TieredItem 的 MAX_DAMAGE。
        // 必须注册在 ArmorDurability 之后 —— 自定义材料的耐久基数来自护甲侧的装配期反推缓存。
        modEventBus.addListener(ToolDurability::onModifyDefaultComponents);

        // 质量系统的玩家效果：移速减益 / 击退抗性 / 重力联动（每 tick 派生）
        NeoForge.EVENT_BUS.register(PlayerMassEffects.class);

        // 护甲值 / 盔甲韧性覆写：在物品属性计算阶段接管 ARMOR / ARMOR_TOUGHNESS 条目
        NeoForge.EVENT_BUS.register(ArmorAttributeHandler.class);
    }
}
