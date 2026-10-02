package dev.newarmorsystem.api;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盔甲纹饰（Armor Trim）对<b>质量系数</b>的增强。
 *
 * <p><b>公式</b>（权重可配置 {@code armor_mass_coefficient.trimMassWeight}，默认 1/6）：
 * <pre>
 *   系数 = 护甲材料系数 × (1 − 权重) + 纹饰材料系数 × 权重
 * </pre>
 * 默认权重的由来是<b>材料数量比</b>：整套护甲需 24 件制作材料，而纹饰只消耗 4 件 → 4 / 24 = 1/6。
 * 权重设为 {@code 0} 即完全关闭纹饰影响（镶嵌后重量与未镶嵌一致）。
 *
 * <p><b>纹饰材料系数的取值顺序</b>：
 * <ol>
 *   <li><b>配置 {@code armor_mass_coefficient.trim_coefficients} 命中</b> → 用配置值
 *       （{@code "<纹饰材料 ID>=<系数>"}）。写完整 ID 最稳，只写 path 的简写也能命中；
 *       同一 target 重复时以<b>最后一条</b>为准。默认值即原先的内置表：
 *       原版非金属 6 项（quartz / redstone / copper / emerald / lapis / amethyst）+
 *       暮色森林的 {@code naga_scale} / {@code carminite}；</li>
 *   <li><b>与护甲材料同注册名</b>的纹饰 → 直接读该材料的质量系数
 *       （{@link PlayerMass#materialCoefficient}）：原版 {@code iron} / {@code gold} /
 *       {@code diamond} / {@code netherite}；模组同理 —— 例如暮色森林的 {@code ironwood} /
 *       {@code fiery} / {@code steeleaf} / {@code knightmetal} 四个纹饰会自动跟随其护甲材料的
 *       系数，<b>随材料登记与 {@code armor_mass_coefficient} 配置联动</b>，无需重复维护。
 *       把配置里对应的默认条目删掉即可回到这条自动跟随（配置优先于它是刻意的）；</li>
 *   <li><b>其它（含数据包新增的纹饰）</b>→ 一律回退 {@code 1.0}。</li>
 * </ol>
 * 第 1 项里原版 {@code copper} / {@code emerald} 也无对应原版材料：默认条目给的是与两个联动模组
 * 材料系数同值的固定值（1.15 = 万物皆铜的铜 / 0.35 = 纯绿宝石工具的绿宝石）；
 * 想恢复"跟随联动模组的材料系数"，把这两条从配置里删掉即可。
 *
 * <p><b>兼容性与安全性</b>：
 * <ul>
 *   <li><b>纯读取</b>：只读取物品上的纹饰信息，不写入任何数据、不注册属性、不使用 Mixin；</li>
 *   <li><b>失败即安全</b>：无纹饰、权重 ≤ 0、纹饰未知等情形一律返回未混合的原系数，
 *       行为与未启用本功能时<b>逐位一致</b>；</li>
 *   <li><b>无注册表遍历</b>：第 1 项在 1.21.1 是注册表<b>按 ID 单点查询</b>，在 1.20.1 是
 *       在「已登记质量系数的材料」小表内按名字比对（见 {@link PlayerMass#materialById}）；
 *       都不做全表遍历、也不依赖实例身份，因此数据包重载、实例更替都不会影响结果；</li>
 *   <li>质量系统总开关关闭时 {@link PlayerMass#getItemMass} 恒返回 0，本类不会被执行。</li>
 * </ul>
 *
 * <p><b>1.20.1 → 1.21.1</b>：纹饰的承载方式不同 —— 1.20.1 是物品 NBT
 * （{@code Trim: {material: "minecraft:iron", pattern: ...}}），1.21.1 是数据组件
 * {@code DataComponents.TRIM}（{@code ArmorTrim.material()} 为 {@code Holder<TrimMaterial>}）。
 * 两者都取"纹饰材料的完整注册名（含命名空间）"，其余逻辑完全一致。
 *
 * @author THEREDK
 */
public final class TrimMass {

    /** 1.20.1 的纹饰 NBT 根键（与 {@code ArmorTrim#getTrim} 的读取方式一致）。 */
    private static final String TRIM_TAG = "Trim";
    /** 纹饰材料在 {@code Trim} 复合标签下的键。 */
    private static final String TRIM_MATERIAL_KEY = "material";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单次日志中最多列出的问题条目数（避免整合包大面积写错时刷屏）。 */
    private static final int MAX_LOGGED_ENTRIES = 8;

    /**
     * 配置层解析出的纹饰系数：<b>完整 ID</b>（含命名空间）→ 系数。
     *
     * <p>由 {@link #applyConfig()} 在加载期与配置重载时<b>整体重建</b>；撤回语义与
     * {@code ItemMassConfig} 一致：删掉条目即回到"同名护甲材料自动跟随 / 1.0"。
     */
    private static final Map<ResourceLocation, Double> CONFIGURED_TRIM_COEFFICIENTS = new ConcurrentHashMap<>();

    /**
     * 仅按 <b>path</b>（不含命名空间）的索引：便于写 {@code "naga_scale=0.65"} 这类简写，
     * 也兜住"模组把纹饰注册在意料之外的命名空间"。同一 path 重复时以最后一条为准。
     */
    private static final Map<String, Double> CONFIGURED_TRIM_COEFFICIENTS_BY_PATH = new ConcurrentHashMap<>();

    private TrimMass() {
    }

    /**
     * 计算经纹饰混合后的质量系数。
     *
     * <p>无纹饰、权重 ≤ 0、纹饰读取失败时，返回值与
     * {@link PlayerMass#materialCoefficient(ArmorMaterial)} 完全一致。
     *
     * @param stack    待计算的护甲物品栈（用于读取纹饰）
     * @param material 该物品的护甲材料
     * @return 混合后的质量系数
     */
    public static double adjustedCoefficient(ItemStack stack, ArmorMaterial material) {
        double base = PlayerMass.materialCoefficient(material);
        double weight = trimMassWeight();
        if (weight <= 0.0) {
            return base;
        }
        ResourceLocation trimId = trimMaterialId(stack);
        if (trimId == null) {
            return base;
        }
        double trim = trimCoefficient(trimId);
        return base * (1.0 - weight) + trim * weight;
    }

    /** 纹饰质量权重（配置未加载时用默认 1/6）。 */
    private static double trimMassWeight() {
        return Config.COMMON_SPEC.isLoaded() ? Config.COMMON.trimMassWeight.get() : 1.0 / 6.0;
    }

    /**
     * 读取该物品的纹饰材料注册名；无纹饰或数据异常时返回 {@code null}。
     *
     * <p>1.20.1：纹饰存于物品 NBT（{@code Trim.material} 为字符串形式的注册名）。
     */
    private static ResourceLocation trimMaterialId(ItemStack stack) {
        CompoundTag trim = stack.getTagElement(TRIM_TAG);
        if (trim == null) {
            return null;
        }
        return ResourceLocation.tryParse(trim.getString(TRIM_MATERIAL_KEY));
    }

    /**
     * 纹饰材料的质量系数：配置命中 → 用配置值；否则与护甲材料同注册名 → 读它的系数；
     * 再否则回退 1.0。
     *
     * @param trimId 纹饰材料的完整注册名（含命名空间）
     */
    private static double trimCoefficient(ResourceLocation trimId) {
        Double configured = CONFIGURED_TRIM_COEFFICIENTS.get(trimId);
        if (configured == null) {
            configured = CONFIGURED_TRIM_COEFFICIENTS_BY_PATH.get(trimId.getPath());
        }
        if (configured != null) {
            return configured;
        }
        ArmorMaterial counterpart = PlayerMass.materialById(trimId);
        if (counterpart != null) {
            return PlayerMass.materialCoefficient(counterpart);
        }
        return 1.0;
    }

    /**
     * 解析 {@code armor_mass_coefficient.trim_coefficients} 并<b>整体重建</b>配置表。
     *
     * <p>格式 {@code "<纹饰材料 ID>=<系数>"}；<b>负数按 0 处理</b>（该纹饰完全不增加质量）。
     * 格式错误（缺 {@code =}、ID 非法、数值非数）的条目跳过并聚合打一条日志，
     * 不会因配置笔误导致加载失败。可安全重复调用（幂等）。
     *
     * <p>刻意<b>不校验</b> ID 是否真实存在 —— 纹饰材料在 1.20.1 属数据包动态注册表，
     * 在加载期查询会引入时序依赖；未命中的条目只是永不生效（无害）。
     */
    public static void applyConfig() {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return;
        }
        Map<ResourceLocation, Double> parsed = new ConcurrentHashMap<>();
        Map<String, Double> parsedByPath = new ConcurrentHashMap<>();
        List<String> malformed = new ArrayList<>();
        for (String raw : Config.COMMON.trimCoefficients.get()) {
            String entry = raw == null ? "" : raw.trim();
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                malformed.add(entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            double coefficient;
            try {
                coefficient = Double.parseDouble(entry.substring(separator + 1).trim());
            } catch (NumberFormatException ex) {
                malformed.add(entry);
                continue;
            }
            if (id == null || !Double.isFinite(coefficient)) {
                malformed.add(entry);
                continue;
            }
            // 负数一律按 0 处理：命中即返回 0，等价于"镶该纹饰不增加质量"
            double value = Math.max(0.0, coefficient);
            parsed.put(id, value);
            parsedByPath.put(id.getPath(), value);
        }
        CONFIGURED_TRIM_COEFFICIENTS.clear();
        CONFIGURED_TRIM_COEFFICIENTS.putAll(parsed);
        CONFIGURED_TRIM_COEFFICIENTS_BY_PATH.clear();
        CONFIGURED_TRIM_COEFFICIENTS_BY_PATH.putAll(parsedByPath);
        if (!malformed.isEmpty()) {
            LOGGER.warn("[NewArmorSystem] armor_mass_coefficient.trim_coefficients: ignored {} malformed "
                            + "entry/entries (expected \"<trim material id>=<coefficient>\"): {}{}",
                    malformed.size(), preview(malformed), more(malformed));
        }
        LOGGER.info("[NewArmorSystem] armor_mass_coefficient.trim_coefficients: applied {} trim "
                + "coefficient(s) from the config file.", parsed.size());
    }

    /**
     * 配置重载：重建纹饰系数表，使本列表的修改即时生效（含"删条目即回到自动跟随"）。
     *
     * <p>只在重载的是本模组的 COMMON 配置时动作（{@code ModConfigEvent.Reloading} 对所有模组
     * 的配置都会派发一次）。
     *
     * @param event 配置重载事件
     */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.COMMON_SPEC) {
            applyConfig();
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
