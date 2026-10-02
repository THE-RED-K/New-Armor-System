package dev.newarmorsystem.api;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置文件版的「材料级质量系数」（与 {@link ItemMassConfig} 同源：层叠式 / 查找期优先）。
 *
 * <pre>{@code
 * [armor_mass_coefficient]
 *     material_coefficients = ["iceandfire:dragonsteel=1.25", "twilightforest:yeti=0.6"]
 * }</pre>
 *
 * <p><b>为什么是层叠而不是调用</b>：本实现<b>不</b>调用
 * {@link PlayerMass#registerMaterialMassCoefficient}，而是把解析结果放进
 * {@link PlayerMass} 的<b>独立配置层</b>，由 {@link PlayerMass#materialCoefficient} 在查找期
 * 优先命中。据此天然获得两条保证，而不需要调用式方案必须自己补的机制：
 * <ol>
 *   <li><b>配置永远赢</b>：优先级体现在查找顺序上，与各模组的登记先后无关；</li>
 *   <li><b>撤回零成本</b>：重载时整体重建配置层，删掉的条目自动停止遮蔽，
 *       代码登记层与内置系数原样恢复，<b>无需快照/回滚</b>。</li>
 * </ol>
 *
 * <p><b>匹配方式</b>：按 {@link net.minecraft.world.item.ArmorMaterial#getName()} 比对 ——
 * 1.20.1 <b>没有护甲材料注册表</b>（材料是 {@code ArmorMaterials} 枚举 + 各模组的自定义实现），
 * 且自定义实现的 {@code getName()} 返回形式<b>没有统一约定</b>，故 {@code "path"} 与
 * {@code "namespace:path"} 两种写法都会入表（与 {@link PlayerMass#materialById} 的比对规则一致）；
 * 同一条目重复时以<b>最后一条</b>为准，写完整名最稳（仅 path 可能跨模组撞车）。
 *
 * <p><b>值语义</b>：正数 = 系数；<b>0 或负数 = 0</b>（该材料完全不增加质量）。
 * 条目<b>不做存在性校验</b>（加载期各类自定义材料尚无统一名字可查），未命中的条目只是永不生效
 * （无害）；格式错误（缺 {@code =}、名字为空、数值非数）的条目跳过并聚合打一条日志，
 * 不会因配置笔误导致加载失败。
 *
 * <p><b>应用时机</b>：由主类在加载期调用一次（见 {@code NewArmorSystem#commonSetup}），
 * 并在配置重载时重建（{@link #onConfigReload}）；因此改动本列表<b>无需重启游戏</b>。
 *
 * @author THEREDK
 */
public final class MaterialMassConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单次日志中最多列出的问题条目数（避免整合包大面积写错时刷屏）。 */
    private static final int MAX_LOGGED_ENTRIES = 8;

    private MaterialMassConfig() {
    }

    /**
     * 解析 {@code armor_mass_coefficient.material_coefficients} 并<b>整体重建</b>配置层。
     *
     * <p>整体替换（而非增量合并）是撤回语义的来源：新配置里没有的材料不再命中，于是回落到
     * 代码登记层 / 内置系数 / 默认 1.0。
     *
     * <p>可安全重复调用（幂等）：每次都以配置内容为准重建，不累积副作用。
     */
    public static void apply() {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return;
        }
        List<? extends String> entries = Config.COMMON.materialCoefficients.get();
        Map<String, Double> parsed = new ConcurrentHashMap<>();
        List<String> malformed = new ArrayList<>();
        int applied = 0;
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.trim();
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                malformed.add(entry);
                continue;
            }
            String name = entry.substring(0, separator).trim();
            double coefficient;
            try {
                coefficient = Double.parseDouble(entry.substring(separator + 1).trim());
            } catch (NumberFormatException ex) {
                malformed.add(entry);
                continue;
            }
            if (name.isEmpty() || !Double.isFinite(coefficient)) {
                malformed.add(entry);
                continue;
            }
            // 负数一律按 0 处理：命中即返回 0，等价于"该材料不增加质量"
            double value = Math.max(0.0, coefficient);
            // 原样、path、namespace:path 三种键都写入：材料名没有统一约定，谁命中算谁
            parsed.put(name, value);
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null) {
                parsed.put(id.getPath(), value);
                parsed.put(id.getNamespace() + ":" + id.getPath(), value);
            }
            applied++;
        }
        PlayerMass.replaceConfigMaterialCoefficients(parsed);
        if (!malformed.isEmpty()) {
            LOGGER.warn("[NewArmorSystem] armor_mass_coefficient.material_coefficients: ignored {} malformed "
                            + "entry/entries (expected \"<armor material name>=<coefficient>\"): {}{}",
                    malformed.size(), preview(malformed), more(malformed));
        }
        LOGGER.info("[NewArmorSystem] armor_mass_coefficient.material_coefficients: applied {} material "
                + "coefficient(s) from the config file.", applied);
    }

    /**
     * 配置重载：重建配置层，使本列表的修改即时生效（含"删条目即恢复代码登记 / 内置系数"）。
     *
     * <p>只在重载的是本模组的 COMMON 配置时动作（{@code ModConfigEvent.Reloading} 对所有模组的
     * 配置都会派发一次）。
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
