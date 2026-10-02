package dev.newarmorsystem.api;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置文件版的「物品接入质量系统」（<b>方案 B：层叠式 / 查找期优先</b>）。
 *
 * <p>整合包/玩家无需写代码，直接在配置里声明
 * {@code item_mass.entries = ["<物品 ID>=<质量>", ...]} 即可让任意物品接入质量系统：
 *
 * <pre>{@code
 * [item_mass]
 *     entries = ["minecraft:trident=8", "minecraft:elytra=4", "mymod:greatsword=12.5"]
 * }</pre>
 *
 * <p><b>为什么是"层叠"而不是"调用"</b>——本实现<b>不</b>调用
 * {@link PlayerMass#registerItemMass}，而是把解析结果放进 {@link PlayerMass} 的
 * <b>独立配置层</b>，由 {@link PlayerMass#getItemMass} 在查找期优先命中。据此天然获得两条保证，
 * 而不需要调用式方案必须自己补的机制：
 * <ol>
 *   <li><b>配置永远赢</b>：优先级体现在查找顺序上，与各模组的登记先后完全无关 ——
 *       不存在"谁后写谁生效"的加载顺序不确定性；</li>
 *   <li><b>撤回零成本</b>：重载时整体重建配置层即可。被删掉的条目自动停止遮蔽，
 *       代码注册层的原值原样恢复，<b>无需快照/回滚</b>，也无需追踪"配置动过哪些物品"。</li>
 * </ol>
 *
 * <p><b>值语义</b>：<b>0 或负数 = 屏蔽</b>（该物品质量恒为 0，即使某模组登记过）；
 * 正数 = 该物品的单件质量。质量按<b>半格</b>取整（{@code getItemMassExact}：
 * 小数部分 &lt; 0.5 向下取整、≥ 0.5 取 0.5），因此 {@code 0.5} 是有效的最小正取值。
 *
 * <p><b>容错</b>：格式错误（缺少 {@code =}、ID 非法、数值非数）与"物品不存在"
 * （对应模组未安装）的条目一律跳过，并按类聚合打一条日志（最多列出
 * {@value #MAX_LOGGED_ENTRIES} 条，避免刷屏），不会因配置笔误导致加载失败。
 *
 * <p><b>应用时机</b>：由主类在 {@link #onModifyDefaultComponents} 调用一次 ——
 * 该事件在<b>全部物品注册完成之后</b>派发，且此时配置已加载；NeoForge 1.21 的注册事件
 * 晚于 common setup，故不能用 {@code FMLCommonSetupEvent} 做物品 ID 解析
 * （与 {@code ToolDurability} 同一时序考量）。配置重载时由 {@link #onConfigReload} 重建，
 * 因此改动本列表<b>无需重启游戏</b>。
 *
 * <p><b>作用范围</b>：质量只在物品被<b>穿戴</b>时计入负重 ——
 * 护甲四槽 / 主手 / 副手 / Curios 饰品槽（见 {@code PlayerMass#collectWornStacks}），
 * 背包内的物品不计入；tooltip 则只要在物品栏即可显示。这是刻意设计。
 *
 * @author THEREDK
 */
public final class ItemMassConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单次日志中最多列出的问题条目数（避免整合包大面积写错时刷屏）。 */
    private static final int MAX_LOGGED_ENTRIES = 8;

    private ItemMassConfig() {
    }

    /**
     * 装配阶段应用：由 {@link ModifyDefaultComponentsEvent} 触发（见类注释的时序说明）。
     *
     * @param event 默认组件修改事件
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        apply();
    }

    /**
     * 解析 {@code item_mass.entries} 并<b>整体重建</b> {@link PlayerMass} 的配置层。
     *
     * <p>整体替换（而非增量合并）是撤回语义的来源：新配置中没有的物品，本层不再命中，
     * 于是 {@link PlayerMass#getItemMass} 落到代码注册层/护甲公式，行为即回到配置之前。
     *
     * <p>可安全重复调用（幂等）：每次都以配置内容为准重建，不累积副作用。
     */
    public static void apply() {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return;
        }
        List<? extends String> entries = Config.COMMON.itemMassEntries.get();
        Map<Item, Double> parsed = new ConcurrentHashMap<>();
        List<String> malformed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.trim();
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                malformed.add(entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            double mass;
            try {
                mass = Double.parseDouble(entry.substring(separator + 1).trim());
            } catch (NumberFormatException ex) {
                malformed.add(entry);
                continue;
            }
            if (id == null || !Double.isFinite(mass)) {
                malformed.add(entry);
                continue;
            }
            // BuiltInRegistries.ITEM 是【默认值注册表】（默认 minecraft:air）：未命中的 ID 返回
            // Items.AIR 而【不是 null】（1.20.1 的 ForgeRegistries.ITEMS.getValue 才返回 null）。
            // 必须先 containsKey 判存在，否则本守卫失效 —— 且质量会被赋给 Items.AIR，
            // 而空槽位的物品正是 AIR（见 PlayerMass#collectWornStacks），会污染每个玩家的穿戴总质量。
            Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
            if (item == null) {
                unknown.add(entry);
                continue;
            }
            // 负数一律按 0 处理：配置层命中即返回 0，等价于"屏蔽该物品的质量"
            parsed.put(item, Math.max(0.0, mass));
        }
        PlayerMass.replaceConfigItemMass(parsed);
        if (!malformed.isEmpty()) {
            LOGGER.warn("[NewArmorSystem] item_mass: ignored {} malformed entry/entries "
                            + "(expected \"<namespace:item>=<mass>\"): {}{}",
                    malformed.size(), preview(malformed), more(malformed));
        }
        if (!unknown.isEmpty()) {
            LOGGER.warn("[NewArmorSystem] item_mass: skipped {} entry/entries whose item is not present "
                            + "(is the mod installed?): {}{}",
                    unknown.size(), preview(unknown), more(unknown));
        }
        LOGGER.info("[NewArmorSystem] item_mass: applied {} item mass override(s) from the config file.",
                parsed.size());
    }

    /**
     * 配置重载：重建配置层，使本列表的修改即时生效（含"删条目即恢复原登记"）。
     *
     * <p>只在重载的是本模组的 COMMON 配置时动作（{@code ModConfigEvent.Reloading} 对所有模组
     * 的配置都会派发一次）。
     *
     * @param event 配置重载事件
     */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.COMMON_SPEC) {
            apply();
        }
    }

    /** 日志用：最多列出前 {@value #MAX_LOGGED_ENTRIES} 条。 */
    private static String preview(List<String> entries) {
        return String.join(" | ", entries.subList(0, Math.min(MAX_LOGGED_ENTRIES, entries.size())));
    }

    /** 日志用：被截断的剩余条数提示。 */
    private static String more(List<String> entries) {
        int rest = entries.size() - MAX_LOGGED_ENTRIES;
        return rest > 0 ? " (+" + rest + " more)" : "";
    }
}
