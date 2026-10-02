package dev.newarmorsystem.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

// Curios 软依赖（可选）：仅当 Curios 加载时遍历其饰品槽，未安装不影响编译与运行
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 质量系统：玩家质量、物品质量与负重比例 —— 全模组统一查询入口。
 *
 * <p><b>玩家质量</b> = 质量基数（默认 50）+ 最大生命值 × 质量系数（默认 0.5）
 * + 穿戴物品的玩家质量加成（{@link #registerItemPlayerMassBoost}，可选）。
 * <b>玩家最大负重质量</b> = 负重系数（默认 1）× 玩家质量。
 * <b>负重比例</b> = 穿戴物品总质量 / 最大负重质量。
 * <b>超重</b> = 穿戴总质量 &gt; 超重系数（配置 {@code player_mass.overloadFactor}，默认 1.0）× 最大负重质量，
 * 等价于"负重比例 &gt; 超重系数"——超重时移速乘数被归零（{@code ×0}，见 {@link PlayerMassEffects#onPlayerTick}）。
 *
 * <p><b>单件护甲质量</b> = 材料系数（经纹饰混合，见 {@link TrimMass}）× 护甲类型系数（AT）×
 * 该件护甲的护甲值，按<b>半格</b>取整（小数部分 &lt; 0.5 向下取整、≥ 0.5 取 0.5）。
 * 材料系数（均可通过配置调整）：皮革 0.3 / 海龟 0.6 / 锁链同铁套 1.0 /
 * 铁 1.0 / 金 1.7 / 钻石 0.45 / 下界合金（无独立配置项，随钻石/金推导）
 * =（钻石 + 金/2）÷ 2 = 1.30 ÷ 2 = 0.65（远古神秘材料，公式结果再减半）。
 *
 * <p><b>通用物品质量注册</b>：任意物品（非护甲亦可，如饰品、重剑）
 * 可通过 {@link #registerItemMass(Item, double)} 注册自定义质量，注册后：
 * <ul>
 *   <li>tooltip 显示 {@code +X 质量} 并参与质量系统展示（负重比例/移速/击退）；</li>
 *   <li>穿戴后计入负重比例与摔落等公式 —— {@link #getTotalWornMass} 统计
 *       护甲四槽 + 主手 + 副手 + Curios 饰品槽（Curios 为软依赖，未安装时
 *       自动跳过饰品槽）。</li>
 * </ul>
 * 注册质量 <b>优先于</b> 护甲材料公式；注册值 ≤ 0 视为取消注册。
 *
 * <p><b>配置文件版接入（推荐给整合包/玩家）</b>：无需写代码，直接在
 * {@code item_mass.entries} 里写 {@code "<物品 ID>=<质量>"} 即可接入任意物品，
 * 该<b>配置层优先于</b>上述代码注册（可覆写模组登记，值 0 可屏蔽）；
 * 协议与撤回语义见 {@link ItemMassConfig}。
 *
 * <p><b>玩家质量加成注册</b>：护甲栏/饰品栏/主手/副手中的物品还可通过
 * {@link #registerItemPlayerMassBoost(Item, double)} 注册<b>玩家质量加成</b>
 * （直接增大 {@link #getPlayerMass} 的结果，用于"重剑/护甲更健壮"之类设定）：
 * <ul>
 *   <li>提升最大负重质量（{@code getMaxLoadMass = 负重系数 × 玩家质量}），
 *       同样穿戴下负重比例降低、可承载更重装备；</li>
 *   <li>同时放大摔落公式中的玩家质量部分（摔落伤害随之升高），
 *       与"质量越大惯性越大"的世界观一致。</li>
 * </ul>
 *
 * <p><b>基准锚点</b>：原版最大生命值环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）
 * 且负重系数 = 1、默认派生因子（击退 2.4；移速减益因子默认 0.0 即关闭）时，负重比例 = 物品总质量 / 60，
 * 每点物品质量对应 +4 击退抗性（击退因子 2.4 → 240 点 ÷ 60；移速减益按 speedPenaltyFactor 换算，
 * 因子 0.6 时每点 -1%，默认 0.0 即不减移速），换算基准一致、便于记忆验证。
 *
 * @author THEREDK
 */
public final class PlayerMass {

    private PlayerMass() {
    }

    /**
     * 通用物品质量注册表：任意物品（含非护甲）→ 自定义质量。
     *
     * <p>由代码调用 {@link #registerItemMass} 写入（附属模组）。
     */
    private static final Map<Item, Double> CUSTOM_ITEM_MASS = new ConcurrentHashMap<>();

    /**
     * <b>配置层</b>：配置文件 {@code item_mass.entries} 声明的物品质量。
     *
     * <p>与 {@link #CUSTOM_ITEM_MASS}（代码注册层）分层存放而非共用一张表，是方案 B 的核心：
     * 配置层在 {@link #getItemMass} 中<b>优先命中</b>，因此
     * <ul>
     *   <li>配置<b>永远赢</b>：与各模组的登记先后顺序无关，不存在"谁后写谁生效"的不确定性；</li>
     *   <li>配置<b>可随时撤回</b>：重载时整体重建本层即可，被删掉的条目自动停止遮蔽，
     *       代码注册层的原值<b>原样恢复</b>（无需快照/回滚，见 {@link ItemMassConfig}）。</li>
     * </ul>
     * 由 {@link #replaceConfigItemMass} 整体替换（仅 {@link ItemMassConfig} 调用）。
     */
    private static final Map<Item, Double> CONFIG_ITEM_MASS = new ConcurrentHashMap<>();

    /** 玩家质量加成注册表：任意物品（护甲/饰品/主手/副手）→ 穿戴时增加的玩家质量。 */
    private static final Map<Item, Double> ITEM_PLAYER_MASS_BOOST = new ConcurrentHashMap<>();

    /**
     * 为任意物品注册自定义质量（非护甲物品亦可，如饰品、重剑）。
     *
     * <p>注册后该物品的质量 <b>优先于</b> 护甲材料公式（对 {@code ArmorItem} 同样生效，
     * 可覆写材料公式结果）；质量值即最终单件质量（tooltip 显示时按<b>半格</b>取整），
     * 不再乘以 AT 系数。<b>注册值 0 = 零质量</b>（与配置层 {@code item_mass.entries} 的
     * {@code ns:item=0} 屏蔽语义一致）；&lt; 0 取消注册。线程安全，可在加载或运行期调用。
     *
     * @param item 目标物品
     * @param mass 质量值（≥ 0 注册，0 = 零质量；&lt; 0 取消注册）
     */
    public static void registerItemMass(Item item, double mass) {
        if (mass >= 0) {
            CUSTOM_ITEM_MASS.put(item, mass);
        } else {
            CUSTOM_ITEM_MASS.remove(item);
        }
    }

    /**
     * 查询物品是否注册了自定义质量。
     *
     * <p>只反映<b>代码注册层</b>（{@link #registerItemMass}）；配置文件声明的条目位于
     * 独立的配置层（见 {@link ItemMassConfig}），不在此列 —— 需要判断"该物品最终是否有
     * 自定义质量"时，请以 {@code getItemMassExact(stack) > 0} 或
     * {@link ItemMassConfig} 的查询结果为准。
     */
    public static boolean hasRegisteredMass(Item item) {
        return CUSTOM_ITEM_MASS.containsKey(item);
    }

    /**
     * 用解析好的配置层<b>整体替换</b>当前配置层（仅 {@link ItemMassConfig} 调用）。
     *
     * <p>整体替换而非增量合并，正是配置重载能正确"撤回"已删条目的原因：
     * 新配置里没有的物品，本层不再命中，代码注册层的值自然重新生效。
     *
     * @param applied 解析后的配置层（null / 空 = 清空配置层）
     */
    static void replaceConfigItemMass(Map<Item, Double> applied) {
        CONFIG_ITEM_MASS.clear();
        if (applied != null && !applied.isEmpty()) {
            CONFIG_ITEM_MASS.putAll(applied);
        }
    }

    /**
     * 为任意物品注册<b>玩家质量加成</b>：穿戴在护甲栏/饰品栏/主手/副手时，
     * 直接增大玩家质量（{@link #getPlayerMass} 结果 += 加成）。
     *
     * <p>与 {@link #registerItemMass}（物品自身质量，计入穿戴总质量）相互独立：
     * 加成<b>提高</b>最大负重并<b>降低</b>负重比例，可用来表现"更健壮"的装备；
     * 注册值 ≤ 0 视为取消注册。线程安全，可在加载或运行期调用。
     *
     * @param item  目标物品
     * @param boost 玩家质量加成（> 0 注册；≤ 0 取消注册）
     */
    public static void registerItemPlayerMassBoost(Item item, double boost) {
        if (boost > 0) {
            ITEM_PLAYER_MASS_BOOST.put(item, boost);
        } else {
            ITEM_PLAYER_MASS_BOOST.remove(item);
        }
    }

    /**
     * 查询物品是否注册了玩家质量加成。
     */
    public static boolean hasPlayerMassBoost(Item item) {
        return ITEM_PLAYER_MASS_BOOST.containsKey(item);
    }

    /** 自定义护甲材料 → 材料质量系数登记表（登记优先于内置 switch 与默认 1.0）。 */
    private static final Map<ArmorMaterial, Double> CUSTOM_MATERIAL_COEFFICIENTS = new IdentityHashMap<>();

    /**
     * <b>配置层</b>的材料质量系数：材料名（{@link ArmorMaterial#getName()} 的返回值，
     * {@code path} 与 {@code namespace:path} 两种写法都会入表）→ 系数。
     *
     * <p>由 {@code MaterialMassConfig} 在加载期与配置重载时<b>整体重建</b>；查询优先级最高
     * （见 {@link #materialCoefficient}），因此既能覆盖模组材料，也能覆盖上面的原版六项配置。
     * 持不可变快照 + volatile：读取端（每帧 tooltip、每 tick 负重）无需加锁。
     */
    private static volatile Map<String, Double> configMaterialCoefficients = Map.of();

    /**
     * 整体替换配置层材料系数表（供 {@code MaterialMassConfig} 调用）。
     *
     * <p>整体替换（而非增量合并）是撤回语义的来源：新配置里没有的材料名不再命中，
     * 于是回落到代码登记层 / 内置系数 / 默认 1.0。
     *
     * @param coefficients 材料名 → 系数；{@code null} 或空表表示清空配置层
     */
    public static void replaceConfigMaterialCoefficients(Map<String, Double> coefficients) {
        configMaterialCoefficients = coefficients == null || coefficients.isEmpty()
                ? Map.of()
                : Map.copyOf(coefficients);
    }

    /**
     * 为护甲材料登记材料质量系数（质量/负重系统中的材料密度因子）。
     *
     * <p>登记优先于内置配置与默认值：原版六材料可用此覆写配置；
     * 自定义材料（其他模组）默认 1.0，可用此修正（例如某第三方重甲材料应有更高质量）。
     * 登记表<b>非线程安全</b>，建议加载期与 {@link ArmorClass#register} 同批调用。
     *
     * <p><b>{@code 0} 是有效值（零质量），不是"取消登记"</b>：与配置层
     * {@link ItemMassConfig} 的 {@code item_mass.entries = ["ns:item=0"]}（0 = 屏蔽该物品质量）
     * 语义一致。二者必须对齐，否则"极轻/无重"这类设计只能靠猜一个正数，
     * 而写 {@code 0.0} 会静默回落到默认 1.0（同铁套密度）—— 与填写者的意图正好相反。
     *
     * @param material    护甲材料（原版 {@link ArmorMaterials} 枚举值或自定义实现）
     * @param coefficient 材料质量系数（≥ 0 注册，0 = 零质量；&lt; 0 取消注册，恢复内置/默认）
     */
    public static void registerMaterialMassCoefficient(ArmorMaterial material, double coefficient) {
        if (coefficient >= 0) {
            CUSTOM_MATERIAL_COEFFICIENTS.put(material, coefficient);
        } else {
            CUSTOM_MATERIAL_COEFFICIENTS.remove(material);
        }
    }

    /**
     * 按注册名查护甲材料实例 —— 供 {@link TrimMass} 的纹饰配对使用
     * （纹饰材料与护甲材料同名时，直接采用该材料的质量系数）。
     *
     * <p><b>1.20.1 没有护甲材料注册表</b>（材料是 {@link ArmorMaterials} 枚举 + 各模组的
     * 自定义 {@link ArmorMaterial} 实现），故改为在「已登记质量系数的材料」与枚举中按
     * {@link ArmorMaterial#getName()} 比对；{@code path} 与 {@code namespace:path} 两种写法
     * 都接受，因为自定义实现的返回形式没有统一约定。
     *
     * <p>表规模很小（仅登记过系数的材料），且本方法只在<b>带纹饰的护甲</b>上被调用，
     * 不构成热路径压力。
     *
     * @param id 纹饰材料的注册名（含命名空间）
     * @return 对应护甲材料；无法匹配时返回 {@code null}
     */
    @Nullable
    public static ArmorMaterial materialById(ResourceLocation id) {
        String path = id.getPath();
        String full = id.getNamespace() + ":" + path;
        for (ArmorMaterial material : CUSTOM_MATERIAL_COEFFICIENTS.keySet()) {
            if (matchesMaterialName(material, path, full)) {
                return material;
            }
        }
        for (ArmorMaterials material : ArmorMaterials.values()) {
            if (matchesMaterialName(material, path, full)) {
                return material;
            }
        }
        return null;
    }

    /** 材料名是否等于 {@code path} 或 {@code namespace:path}（自定义实现两种写法都可能返回）。 */
    private static boolean matchesMaterialName(ArmorMaterial material, String path, String full) {
        String name = material.getName();
        return name != null && (name.equals(path) || name.equals(full));
    }

    /**
     * 材料质量系数：<b>配置层</b> &gt; 代码登记 &gt; 内置配置 &gt; 默认 1.0（同铁套）。
     *
     * <p>六种材料均可通过配置调整（{@code armor_mass_coefficient} 分组），
     * 未加载配置时回退本处默认值；取值与现实密度仅弱相关，主要按游戏平衡性校准。
     * 下界合金无独立配置项：作为远古神秘材料，其系数 =（钻石 + 金/2）÷ 2，
     * 随钻石与金的配置值动态推导；未登记的自定义材料默认 1.0（同铁套）。
     *
     * <p><b>配置层</b>（{@code armor_mass_coefficient.material_coefficients}）按
     * {@link ArmorMaterial#getName()} 匹配材料名，<b>优先于上述一切</b> —— 模组材料因此不必改代码
     * 即可调整，原版六项也能被它覆写（如 {@code "minecraft:iron=0.9"}）。
     *
     * @param material 护甲材料
     * @return 材料质量系数
     */
    public static double materialCoefficient(ArmorMaterial material) {
        // 配置层优先（按材料名匹配；path 与 namespace:path 两种写法都在表里）
        String name = material.getName();
        if (name != null) {
            Double configured = configMaterialCoefficients.get(name);
            if (configured != null) {
                return configured;
            }
        }
        Double registered = CUSTOM_MATERIAL_COEFFICIENTS.get(material);
        if (registered != null) {
            return registered; // 登记优先（含覆写内置）
        }
        if (!(material instanceof ArmorMaterials am)) {
            return 1.0;
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return switch (am) {
            case LEATHER -> loaded ? Config.COMMON.leatherMassCoefficient.get() : 0.3;
            case TURTLE -> loaded ? Config.COMMON.turtleMassCoefficient.get() : 0.6;
            case CHAIN -> loaded ? Config.COMMON.chainMassCoefficient.get() : 1.0;
            case IRON -> loaded ? Config.COMMON.ironMassCoefficient.get() : 1.0;
            case GOLD -> loaded ? Config.COMMON.goldMassCoefficient.get() : 1.7;
            case DIAMOND -> loaded ? Config.COMMON.diamondMassCoefficient.get() : 0.45;
            // 下界合金：(钻石 + 金/2) / 2，随钻石/金配置动态推导（默认 0.65），再次 / 2 是世界观修正
            case NETHERITE -> loaded
                    ? (Config.COMMON.diamondMassCoefficient.get() + Config.COMMON.goldMassCoefficient.get() / 2) / 2
                    : (0.45 + 1.7 / 2) / 2;
            default -> 1.0;
        };
    }

    /**
     * 玩家质量 = 质量基数 + 最大生命值 × 质量系数 + 穿戴物品的玩家质量加成
     * （护甲栏/饰品栏/主手/副手，{@link #registerItemPlayerMassBoost}）。
     *
     * <p>基于<b>最大生命值</b>而非当前生命值：质量/负重是角色固有属性，
     * 不随当前血量波动，保证受伤后负重比例、移速减益与击退抗性保持稳定。
     *
     * <p><b>功能总开关</b>：{@code feature_toggles.massSystemEnabled} 关闭时返回
     * <b>原版等效质量</b>（以原版 maxHP=20 代入同一公式、不计穿戴加成，默认值下为 60）——
     * 这样摔落公式的 mass 项回到 60（默认参数下 D = h - 3，与原版一致），
     * 而负载比例因 {@link #getTotalWornMass} 归零而恒为 0，
     * 移速减益 / 击退抗性 / 质量 tooltip 一并失效。
     */
    public static double getPlayerMass(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double base = loaded ? Config.COMMON.massBase.get() : 50;
        double coefficient = loaded ? Config.COMMON.massHealthCoefficient.get() : 0.5;
        if (loaded && !Config.COMMON.massSystemEnabled.get()) {
            return base + 20.0 * coefficient;
        }
        return base + player.getMaxHealth() * coefficient + getWornPlayerMassBoost(player);
    }

    /**
     * 玩家最大负重质量 = 负重系数 × 玩家质量。
     */
    public static double getMaxLoadMass(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.loadFactor.get() : 1.0;
        return factor * getPlayerMass(player);
    }

    /**
     * 单件物品质量（<b>精确版</b>，最小刻度 0.5）。优先级：
     * <ol>
     *   <li>broken 物品 → 0（质量效果随功能一并失效）；</li>
     *   <li><b>配置层</b>：配置文件 {@code item_mass.entries} 声明的物品
     *       （见 {@link ItemMassConfig}）→ 配置值；</li>
     *   <li>代码注册层：{@link #registerItemMass} 注册过的物品（含非护甲）→ 注册值；</li>
     *   <li>{@link ArmorItem} → 材料系数（经纹饰混合，见 {@link TrimMass}）× AT × 生效护甲值；
     *       其中生效护甲值优先取 {@link ArmorAttributeRules} 的覆写值（护甲值 API 与质量系统
     *       共享同一数据源），未覆写时回退 {@code ArmorItem.getDefense()} 原版值；</li>
     *   <li>其余非护甲物品 → 0。</li>
     * </ol>
     *
     * <p><b>取整规则</b>（见 {@link #quantizeMass}）：小数部分 &lt; 0.5 → 向下取整；
     * ≥ 0.5 → 取 0.5。即最小刻度为<b>半格</b>，比旧的"整数向下取整"精确一倍，
     * 使纹饰等亚整数差异不再被取整吃掉。
     *
     * <p>配置层排在代码注册层之前是刻意的：整合包只要写上一条配置就能覆写任何模组的登记，
     * 且删掉该条目即刻恢复原登记（重载时本层整体重建，见 {@link #replaceConfigItemMass}）。
     *
     * @param stack 物品栈
     * @return 物品质量（半格刻度）
     */
    public static double getItemMassExact(ItemStack stack) {
        if (Config.COMMON_SPEC.isLoaded() && !Config.COMMON.massSystemEnabled.get()) {
            return 0.0;   // 功能总开关关闭：物品质量一律为 0（tooltip 亦不再显示质量行）
        }
        if (BrokenState.isBroken(stack)) {
            return 0.0;
        }
        Item item = stack.getItem();
        // 配置层最高优先：整合包/玩家无需写代码即可接入或覆写质量（用 0 可屏蔽其它模组的登记）
        Double configured = CONFIG_ITEM_MASS.get(item);
        if (configured != null) {
            return quantizeMass(configured);
        }
        Double registered = CUSTOM_ITEM_MASS.get(item);
        if (registered != null) {
            return quantizeMass(registered);
        }
        if (!(item instanceof ArmorItem armor)) {
            return 0.0;
        }
        // 护甲值取"生效值"：ArmorAttributeRules 覆写优先（护甲值 API 与质量系统共享同一数据源），
        // 未覆写时回退 ArmorItem 构造时固化的原始值 getDefense()，避免"改了护甲值质量却不变"的脱节
        double defense = armor.getDefense();
        ArmorAttributeRules.ArmorStats stats = ArmorAttributeRules.statsOf(armor);
        if (stats != null) {
            defense = stats.armor();
        }
        // 质量系数经纹饰混合：无纹饰 / 权重为 0 时结果与原来完全一致（见 TrimMass）
        double mass = TrimMass.adjustedCoefficient(stack, armor.getMaterial())
                * ArmorClass.of(armor.getMaterial()).ArmorTypeCoefficient()
                * defense;
        return quantizeMass(mass);
    }

    /**
     * 单件物品质量的<b>整数近似版</b>（= {@code floor(getItemMassExact(stack))}）。
     *
     * <p>保留此方法仅为兼容既有调用者；<b>新代码请用 {@link #getItemMassExact}</b> ——
     * 半格刻度（如 3.5）在这里会被丢掉小数部分。
     *
     * @param stack 物品栈
     * @return 物品质量向下取整后的整数值
     */
    public static int getItemMass(ItemStack stack) {
        return (int) Math.floor(getItemMassExact(stack));
    }

    /**
     * 质量取整：<b>小数部分 &lt; 0.5 → 向下取整；≥ 0.5 → 取 0.5</b>（最小刻度半格）。
     *
     * <p>等价于 {@code floor(x * 2) / 2}，但额外加 {@link #MASS_EPSILON} 兜住浮点误差：
     * 材料系数可能是 {@code 1/6} 这类二进制无限小数，乘积有可能落在半格边界<b>略偏下</b>处
     * （本应 3.5 却算出 3.4999999999999996），不加容差会白丢半格。
     *
     * @param mass 原始质量
     * @return 半格刻度质量
     */
    private static double quantizeMass(double mass) {
        if (mass <= 0.0) {
            return 0.0;
        }
        return Math.floor(mass * 2.0 + MASS_EPSILON) / 2.0;
    }

    /**
     * 质量取整容差：远大于该量级的 double 误差（ulp ≈ 1e-14），又远小于半格 0.5，
     * 既能兜住"本应落在半格边界"的浮点误差，也不会误伤真实取值。
     */
    private static final double MASS_EPSILON = 1e-9;

    /**
     * 玩家当前穿戴物品的总质量（<b>精确版</b>，半格刻度）——
     * {@link #collectWornStacks} 遍历的所有槽位之和。
     *
     * <p>护甲四槽 + 主手 + 副手天然计入；注册了自定义质量的非护甲物品
     * （{@link #registerItemMass}，如饰品、重剑）放在其中任一槽位即计入；
     * 若 Curios 已安装，其饰品槽亦计入（软依赖，未安装自动跳过）。
     *
     * <p><b>累加方式</b>：以"半单位整数"累加再除以 2 —— 单件质量都是 0.0/0.5 的整数倍，
     * 因此求和既<b>精确</b>（无浮点累加误差）又必然仍是半格刻度，
     * 不会出现"单件显示 3.5、总和却差半格"的不一致。
     *
     * <p><b>功能总开关</b>：{@code massSystemEnabled} 关闭时恒返回 0 ——
     * 负载比例随之为 0，移速减益 / 击退抗性 / 质量 tooltip 全部失效。
     */
    public static double getTotalWornMassExact(Player player) {
        if (Config.COMMON_SPEC.isLoaded() && !Config.COMMON.massSystemEnabled.get()) {
            return 0.0;
        }
        long halves = 0;
        for (ItemStack stack : collectWornStacks(player)) {
            halves += Math.round(getItemMassExact(stack) * 2.0);
        }
        return halves / 2.0;
    }

    /**
     * 玩家当前穿戴物品总质量的<b>整数近似版</b>（= {@code floor(getTotalWornMassExact(player))}）。
     *
     * <p>保留此方法仅为兼容既有调用者；<b>新代码请用 {@link #getTotalWornMassExact}</b>。
     *
     * @param player 玩家
     * @return 总质量向下取整后的整数值
     */
    public static int getTotalWornMass(Player player) {
        return (int) Math.floor(getTotalWornMassExact(player));
    }

    /**
     * 玩家穿戴物品提供的<b>玩家质量加成</b>总和：护甲栏/饰品栏/主手/副手内
     * 所有注册了 {@link #registerItemPlayerMassBoost} 的物品加成之和。
     */
    public static double getWornPlayerMassBoost(Player player) {
        double boost = 0;
        for (ItemStack stack : collectWornStacks(player)) {
            Double registered = ITEM_PLAYER_MASS_BOOST.get(stack.getItem());
            if (registered != null) {
                boost += registered;
            }
        }
        return boost;
    }

    /**
     * 收集玩家当前穿戴的全部物品栈：护甲四槽 + 主手 + 副手 +
     * Curios 饰品槽（若安装）。
     *
     * <p>Curios 为软依赖：{@code ModList.isLoaded("curios")} 为 false 时跳过
     * 饰品槽遍历，仅返回原版槽位，保证未安装 Curios 也能正常运行。
     */
    private static List<ItemStack> collectWornStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(16);
        stacks.addAll(player.getInventory().armor);
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        if (ModList.get().isLoaded("curios")) {
            ICuriosItemHandler handler = player.getCapability(CuriosCapability.INVENTORY).orElse(null);
            if (handler != null) {
                for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                    IDynamicStackHandler slots = stacksHandler.getStacks();
                    for (int i = 0; i < slots.getSlots(); i++) {
                        stacks.add(slots.getStackInSlot(i));
                    }
                }
            }
        }
        return stacks;
    }

    /**
     * 负重比例 = 穿戴总质量 / 最大负重质量。
     *
     * <p>穿戴超过上限时比例 > 1，由调用方决定是否警示。
     *
     * @return 比例（0 表示无穿戴或最大负重质量非正）
     */
    public static double getLoadRatio(Player player) {
        double max = getMaxLoadMass(player);
        return max <= 0 ? 0 : getTotalWornMassExact(player) / max;
    }

    /**
     * 超重判定系数（配置 {@code player_mass.overloadFactor}，默认 1.0）——
     * 判定式为「穿戴总质量 &gt; 超重系数 × 最大负重质量」，等价于「负重比例 &gt; 超重系数」。
     *
     * <p>分母是<b>实时</b>最大负重质量（= 负重系数 × 玩家质量，随最大生命值/玩家质量加成变化），
     * 因此"当前能穿多少"始终以玩家此刻的属性为准。系数 1.0 即"只有超出最大负重才算超重"；
     * 上调给出缓冲带（未满载不罚），下调让玩家在未满载前就受罚。
     * 配置未加载时返回 1.0（与默认值一致）。
     */
    public static double getOverloadFactor() {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        return loaded ? Config.COMMON.overloadFactor.get() : 1.0;
    }

    /**
     * 是否<b>超重</b>：负重比例 &gt; 超重系数（默认 1.0，见 {@link #getOverloadFactor()}）。
     *
     * <p>边界的浮点误差用 {@link #MASS_EPSILON} 兜住 —— 质量是半格刻度、
     * 最大负重质量是乘出来的浮点值，恰好"压线"时不应被判成超重。
     *
     * <p>超重的后果由 {@link PlayerMassEffects} 施加：<b>移速、攻速、游泳推进三项乘数归零</b>
     * （用超大负数把属性压到下限 0，其它模组的加成无法抵消），由
     * {@code feature_toggles.overloadZeroEnabled}（默认开启）门控；关闭时超重不影响移速/攻速
     * （击退抗性封顶与摔落质量项仍照常）。
     *
     * @return 是否超重
     */
    public static boolean isOverloaded(Player player) {
        return getLoadRatio(player) > getOverloadFactor() + MASS_EPSILON;
    }

    /**
     * 击退抗性属性值（0.0 ~ 1.0）= min(1.0, 负重比例 × 击退抗性因子)。
     *
     * <p>击退抗性因子可配置（{@code knockbackResistanceFactor}，默认 2.4）。
     * 换算：原版属性 0.0~1.0，显示时 ×100 视为"点"（最大值 100 点），
     * 默认 2.4 时满负载 240 点；超出部分（>100 点）无效，
     * 封顶 100 点 = 属性 1.0（约 42% 负载即封顶，刻意调高以缓解"击退抗性太难获取"）。无护甲（负重比例 0）时为 0，即"初始击退抗性为 0"。
     *
     * <p>基准锚点：原版 maxHP=20 环境（玩家质量 60）下每点护甲质量对应
     * 因子×5/3 点击退抗性（默认 2.4 → 240 点 ÷ 60 = 4 点/质量；达到 100 点封顶需 25 点护甲质量）。
     *
     * <p>本公式结果恒非负（负重比例 ≥ 0）。<b>负数击退抗性由附属模组提供</b>：
     * 属性范围默认<b>无下限</b>（{@code Config.COMMON.knockbackResistanceNoLowerLimit}
     * 默认开启），附属直接为玩家 {@code Attributes.KNOCKBACK_RESISTANCE} 添加负修饰符
     * 即可生效 ——
     * 原版击退公式 {@code 击退强度 × (1 - 抗性)} 对负数天然成立：负抗性 = 击退更远
     * （-0.5 → ×1.5，-1.0 → ×2.0）。{@link PlayerMassEffects} 每 tick 仅更新
     * 本类固定 UUID 的修饰符，不会移除外来负修饰符。
     */
    public static double getKnockbackResistance(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 2.4;
        return Math.min(1.0, getLoadRatio(player) * factor);
    }

    /**
     * 击退抗性显示点数（0.0 ~ 100.0）= 属性值 × 100，供 tooltip 直接展示 "+X"。
     */
    public static double getKnockbackResistancePoints(Player player) {
        return getKnockbackResistance(player) * 100;
    }

    /**
     * 本模组当前是否负责供给击退抗性 —— 即「质量系统」与「击退抗性」两个开关<b>同时开启</b>。
     *
     * <p><b>为什么需要这个判定</b>：护甲自带的击退抗性（如原版下界合金每件 {@code +0.1}）
     * 在本模组开启时会被 {@code ItemStackMixin} <b>剔除</b>，改由本类按负重统一派生。
     * 若关掉质量系统（或单独关掉击退抗性）后仍继续剔除，玩家就会
     * <b>既拿不到本模组的抗性、也拿不回原版护甲的抗性</b> —— 变成纯粹的削弱，
     * 违反「关闭后完全回到原版行为」的约定。故剔除动作必须以本判定为前提：
     * 关闭时不再剔除，原版护甲抗性原样回归。
     *
     * <p>配置未加载时返回 {@code true}（与默认值一致，默认开启）。
     *
     * @return 是否由本模组供给击退抗性
     */
    public static boolean suppliesKnockbackResistance() {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return true;
        }
        return Config.COMMON.massSystemEnabled.get() && Config.COMMON.knockbackResistanceEnabled.get();
    }

    /**
     * 移速减益比例 = 负重比例 × 移速减益因子（可配置
     * {@code speedPenaltyFactor}，<b>默认 0.0 即不减速</b>）。
     *
     * <p><b>默认关闭的原因</b>：本属性是为「整合包中献祭移速换减伤」设计的，
     * 对原版节奏的玩家而言负担过重，故默认因子为 0.0（移速恒不受负重影响）。
     * <b>整合包作者</b>若需要该玩法，把 {@code speedPenaltyFactor} 设为 0.6
     * 即回到设计值（满负载时 -60% 移速）。
     *
     * <p>表示移动速度的减少幅度：移速 = 基础移速 × (1 - 比例)；
     * 超重（负重比例 &gt; 超重系数）时减益继续上升，由原版移速属性下限（0）兜底
     * —— 但超重归零开启时（{@code overloadZeroEnabled}，默认开启）移速已被压到 0，
     * 本值只在<b>未超重</b>的区间内可见。
     *
     * <p>基准锚点：原版 maxHP=20 环境（玩家质量 60）下每点护甲质量对应
     * -因子/60 移速（因子 0.6 → -1% 移速；满负载 60 点质量减 60%）。
     *
     * <p>受 {@code feature_toggles.massSystemEnabled} 与
     * {@code feature_toggles.speedPenaltyEnabled} 双重门控（任一关闭时本值不再被施加）。
     */
    public static double getSpeedPenalty(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.0;
        return getLoadRatio(player) * factor;
    }

    /**
     * 预览负重比例：<b>仅该物品自身质量</b>占最大负重的比例
     * （{@code 物品质量 / 最大负重质量}），反映"穿上这件物品将提供的负重"。
     *
     * <p><b>不叠加当前穿戴总质量</b>，也不做槽位替换计算 —— 预览独立于
     * 玩家当前穿什么（护甲栏/主手/副手/饰品槽上已有的装备），与位置无关。
     */
    public static double getPreviewLoadRatio(Player player, double additionalMass) {
        double max = getMaxLoadMass(player);
        return max <= 0 ? 0 : additionalMass / max;
    }

    /**
     * 预览移速减益比例（0.0 ~ 1.0）= 预览负重比例 × 移速减益因子
     * （{@code speedPenaltyFactor}，<b>默认 0.0 即不减速</b>），与
     * {@link #getSpeedPenalty} 公式一致，仅将负重比例替换为"该物品单独提供"的预览值。
     */
    public static double getPreviewSpeedPenalty(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.0;
        return getPreviewLoadRatio(player, additionalMass) * factor;
    }

    /**
     * 预览击退抗性显示点数（0.0 ~ 100.0）= min(1.0, 预览负重比例 × 击退抗性因子（默认 2.4）) × 100，
     * 与 {@link #getKnockbackResistancePoints} 公式一致，仅将负重比例替换为
     * "该物品单独提供"的预览值。
     */
    public static double getPreviewKnockbackResistancePoints(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 2.4;
        return Math.min(1.0, getPreviewLoadRatio(player, additionalMass) * factor) * 100;
    }
}
