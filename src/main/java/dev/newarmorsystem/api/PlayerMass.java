package dev.newarmorsystem.api;

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
 *
 * <p><b>单件护甲质量</b> = 材料系数 × 护甲类型系数（AT）× 该件护甲的护甲值，向下取整。
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
 * 且负重系数 = 1、默认派生因子（击退 1.2 / 移速 0.6）时，负重比例 = 物品总质量 / 60，
 * 每点物品质量恰好对应 -1% 移速（60% ÷ 60）与 +2 击退抗性（120 点 ÷ 60），
 * 换算基准一致、便于记忆验证。
 *
 * @author THEREDK
 */
public final class PlayerMass {

    private PlayerMass() {
    }

    /** 通用物品质量注册表：任意物品（含非护甲）→ 自定义质量。 */
    private static final Map<Item, Double> CUSTOM_ITEM_MASS = new ConcurrentHashMap<>();

    /** 玩家质量加成注册表：任意物品（护甲/饰品/主手/副手）→ 穿戴时增加的玩家质量。 */
    private static final Map<Item, Double> ITEM_PLAYER_MASS_BOOST = new ConcurrentHashMap<>();

    /**
     * 为任意物品注册自定义质量（非护甲物品亦可，如饰品、重剑）。
     *
     * <p>注册后该物品的质量 <b>优先于</b> 护甲材料公式（对 {@code ArmorItem} 同样生效，
     * 可覆写材料公式结果）；质量值即最终单件质量（tooltip 显示时向下取整），
     * 不再乘以 AT 系数。注册值 ≤ 0 视为取消注册。线程安全，可在加载或运行期调用。
     *
     * @param item 目标物品
     * @param mass 质量值（> 0 注册；≤ 0 取消注册）
     */
    public static void registerItemMass(Item item, double mass) {
        if (mass > 0) {
            CUSTOM_ITEM_MASS.put(item, mass);
        } else {
            CUSTOM_ITEM_MASS.remove(item);
        }
    }

    /**
     * 查询物品是否注册了自定义质量。
     */
    public static boolean hasRegisteredMass(Item item) {
        return CUSTOM_ITEM_MASS.containsKey(item);
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
     * 为护甲材料登记材料质量系数（质量/负重系统中的材料密度因子）。
     *
     * <p>登记优先于内置配置与默认值：原版六材料可用此覆写配置；
     * 自定义材料（其他模组）默认 1.0，可用此修正（例如某第三方重甲材料应有更高质量）。
     * 登记表<b>非线程安全</b>，建议加载期与 {@link ArmorClass#register} 同批调用。
     *
     * @param material    护甲材料（原版 {@link ArmorMaterials} 枚举值或自定义实现）
     * @param coefficient 材料质量系数（> 0 注册；≤ 0 取消注册，恢复内置/默认）
     */
    public static void registerMaterialMassCoefficient(ArmorMaterial material, double coefficient) {
        if (coefficient > 0) {
            CUSTOM_MATERIAL_COEFFICIENTS.put(material, coefficient);
        } else {
            CUSTOM_MATERIAL_COEFFICIENTS.remove(material);
        }
    }

    /**
     * 材料质量系数：登记优先 &gt; 内置配置 &gt; 默认 1.0（同铁套）。
     *
     * <p>六种材料均可通过配置调整（{@code armor_mass_coefficient} 分组），
     * 未加载配置时回退本处默认值；取值与现实密度仅弱相关，主要按游戏平衡性校准。
     * 下界合金无独立配置项：作为远古神秘材料，其系数 =（钻石 + 金/2）÷ 2，
     * 随钻石与金的配置值动态推导；未登记的自定义材料默认 1.0（同铁套）。
     *
     * @param material 护甲材料
     * @return 材料质量系数
     */
    public static double materialCoefficient(ArmorMaterial material) {
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
     */
    public static double getPlayerMass(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double base = loaded ? Config.COMMON.massBase.get() : 50;
        double coefficient = loaded ? Config.COMMON.massHealthCoefficient.get() : 0.5;
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
     * 单件物品质量（向下取整）。优先级：
     * <ol>
     *   <li>broken 物品 → 0（质量效果随功能一并失效）；</li>
     *   <li>注册了自定义质量的物品（{@link #registerItemMass}，含非护甲）→ 注册值；</li>
     *   <li>{@link ArmorItem} → floor(材料系数 × AT × 生效护甲值)；其中生效护甲值优先取
     *       {@link ArmorAttributeRules} 的覆写值（护甲值 API 与质量系统共享同一数据源），
     *       未覆写时回退 {@code ArmorItem.getDefense()} 原版值；</li>
     *   <li>其余非护甲物品 → 0。</li>
     * </ol>
     *
     * @param stack 物品栈
     * @return 物品质量（向下取整）
     */
    public static int getItemMass(ItemStack stack) {
        if (BrokenState.isBroken(stack)) {
            return 0;
        }
        Item item = stack.getItem();
        Double registered = CUSTOM_ITEM_MASS.get(item);
        if (registered != null) {
            return (int) Math.floor(registered);
        }
        if (!(item instanceof ArmorItem armor)) {
            return 0;
        }
        // 护甲值取"生效值"：ArmorAttributeRules 覆写优先（护甲值 API 与质量系统共享同一数据源），
        // 未覆写时回退 ArmorItem 构造时固化的原始值 getDefense()，避免"改了护甲值质量却不变"的脱节
        double defense = armor.getDefense();
        ArmorAttributeRules.ArmorStats stats = ArmorAttributeRules.statsOf(armor);
        if (stats != null) {
            defense = stats.armor();
        }
        double mass = materialCoefficient(armor.getMaterial())
                * ArmorClass.of(armor.getMaterial()).ArmorTypeCoefficient()
                * defense;
        return (int) Math.floor(mass);
    }

    /**
     * 玩家当前穿戴物品的总质量（{@link #collectWornStacks} 遍历的所有槽位之和）。
     *
     * <p>护甲四槽 + 主手 + 副手天然计入；注册了自定义质量的非护甲物品
     * （{@link #registerItemMass}，如饰品、重剑）放在其中任一槽位即计入；
     * 若 Curios 已安装，其饰品槽亦计入（软依赖，未安装自动跳过）。
     */
    public static int getTotalWornMass(Player player) {
        int total = 0;
        for (ItemStack stack : collectWornStacks(player)) {
            total += getItemMass(stack);
        }
        return total;
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
        return max <= 0 ? 0 : getTotalWornMass(player) / max;
    }

    /**
     * 击退抗性属性值（0.0 ~ 1.0）= min(1.0, 负重比例 × 击退抗性因子)。
     *
     * <p>击退抗性因子可配置（{@code knockbackResistanceFactor}，默认 1.2）。
     * 换算：原版属性 0.0~1.0，显示时 ×100 视为"点"（最大值 100 点），
     * 默认 1.2 时满负载 120 点 = 属性 1.2；溢出部分（>100 点）无效，
     * 封顶 100 点 = 属性 1.0。无护甲（负重比例 0）时为 0，即"初始击退抗性为 0"。
     *
     * <p>基准锚点：原版 maxHP=20 环境（玩家质量 60）下每点护甲质量对应
     * 因子×2 点击退抗性（默认 1.2 → 120 点 ÷ 60 = 2 点/质量；达到 100 点封顶需 50 点护甲质量）。
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
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 1.2;
        return Math.min(1.0, getLoadRatio(player) * factor);
    }

    /**
     * 击退抗性显示点数（0.0 ~ 100.0）= 属性值 × 100，供 tooltip 直接展示 "+X"。
     */
    public static double getKnockbackResistancePoints(Player player) {
        return getKnockbackResistance(player) * 100;
    }

    /**
     * 移速减益比例 = 负重比例 × 移速减益因子（可配置
     * {@code speedPenaltyFactor}，默认 0.6，满负载时减 60%）。
     *
     * <p>表示移动速度的减少幅度：移速 = 基础移速 × (1 - 比例)，
     * 负重比例 1（满负载）时减 60%（默认因子）；超重（比例 > 1）时减益继续上升，
     * 由原版移速属性下限（0）兜底。
     *
     * <p>基准锚点：原版 maxHP=20 环境（玩家质量 60）下每点护甲质量对应
     * -因子/60 移速（默认 0.6 → -1% 移速；满负载 60 点质量减 60%）。
     */
    public static double getSpeedPenalty(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.6;
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
     * 预览移速减益比例（0.0 ~ 0.6）= 预览负重比例 × 0.6，与
     * {@link #getSpeedPenalty} 公式一致，仅将负重比例替换为"该物品单独提供"的预览值。
     */
    public static double getPreviewSpeedPenalty(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.6;
        return getPreviewLoadRatio(player, additionalMass) * factor;
    }

    /**
     * 预览击退抗性显示点数（0.0 ~ 100.0）= min(1.0, 预览负重比例 × 1.2) × 100，
     * 与 {@link #getKnockbackResistancePoints} 公式一致，仅将负重比例替换为
     * "该物品单独提供"的预览值。
     */
    public static double getPreviewKnockbackResistancePoints(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 1.2;
        return Math.min(1.0, getPreviewLoadRatio(player, additionalMass) * factor) * 100;
    }
}
