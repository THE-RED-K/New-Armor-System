package dev.newarmorsystem.api;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 质量系统：玩家质量、物品质量与负重比例 —— 全模组统一查询入口。
 *
 * <p><b>玩家质量</b> = 质量基数（默认 50）+ 最大生命值 × 质量系数（默认 0.5）
 * + 穿戴物品的玩家质量加成（{@link #registerItemPlayerMassBoost}，可选）。
 * <b>玩家最大负重质量</b> = 负重系数（默认 1）× 玩家质量。
 * <b>负重比例</b> = 穿戴物品总质量 / 最大负重质量。
 *
 * <p><b>单件护甲质量</b> = 材料系数 × 护甲类型系数（AT）× 该件护甲的护甲值，向下取整。
 * 材料系数：皮革 0.3 / 海龟 0.6 / 锁链同铁套 1.0 / 铁 1.0 / 金 1.7 / 钻石 0.45 /
 * 下界合金 =（钻石 + 金/2）÷ 2 = 0.65（远古神秘材料，公式结果再减半）。
 *
 * <p><b>1.20.1 → 1.21.1 适配</b>：
 * <ul>
 *   <li>{@code ArmorMaterials} 由枚举变注册表条目 → 材料判定改为按条目实例比对
 *       （{@code ArmorMaterials.X.value()}），{@code armor.getMaterial()} 需再取 {@code .value()}；</li>
 *   <li>不再直接读 {@code Inventory#armor} 字段，改用公开的
 *       {@link Player#getItemBySlot(EquipmentSlot)}；</li>
 *   <li>护甲值取「生效值」：{@link ArmorAttributeRules#statsOf(Item)} 覆写优先，
 *       未覆写时回退 {@link ArmorItem#getDefense()}（1.21 仍存在，内部即
 *       {@code material.getDefense(type)}）；</li>
 *   <li><b>Curios 饰品槽暂未接入</b>（1.21.1 的 Curios 依赖与 API 需单独引入），
 *       当前仅统计护甲四槽 + 主手 + 副手。</li>
 * </ul>
 *
 * <p><b>基准锚点</b>：原版最大生命值环境（maxHP=20 → 玩家质量 = 50 + 20×0.5 = 60）
 * 且负重系数 = 1、默认派生因子（击退 1.2 / 移速 0.6）时，负重比例 = 物品总质量 / 60，
 * 每点物品质量恰好对应 -1% 移速（60% ÷ 60）与 +2 击退抗性（120 点 ÷ 60）。
 *
 * @author THEREDK
 */
public final class PlayerMass {

    private PlayerMass() {
    }

    /** 护甲四槽（1.21 起不再直接读 Inventory#armor 字段，改用公开的 getItemBySlot）。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 通用物品质量注册表：任意物品（含非护甲）→ 自定义质量。 */
    private static final Map<Item, Double> CUSTOM_ITEM_MASS = new ConcurrentHashMap<>();

    /** 玩家质量加成注册表：任意物品（护甲/饰品/主手/副手）→ 穿戴时增加的玩家质量。 */
    private static final Map<Item, Double> ITEM_PLAYER_MASS_BOOST = new ConcurrentHashMap<>();

    /** 自定义护甲材料 → 材料质量系数登记表（登记优先于内置与默认 1.0）。 */
    private static final Map<ArmorMaterial, Double> CUSTOM_MATERIAL_COEFFICIENTS = new IdentityHashMap<>();

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

    /** 查询物品是否注册了自定义质量。 */
    public static boolean hasRegisteredMass(Item item) {
        return CUSTOM_ITEM_MASS.containsKey(item);
    }

    /**
     * 为任意物品注册<b>玩家质量加成</b>：穿戴在护甲栏/主手/副手时，
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

    /** 查询物品是否注册了玩家质量加成。 */
    public static boolean hasPlayerMassBoost(Item item) {
        return ITEM_PLAYER_MASS_BOOST.containsKey(item);
    }

    /**
     * 为护甲材料登记材料质量系数（质量/负重系统中的材料密度因子）。
     *
     * <p>登记优先于内置配置与默认值：原版材料可用此覆写配置；自定义材料默认 1.0，
     * 可用此修正（例如某第三方重甲材料应有更高质量）。
     * 登记表<b>非线程安全</b>，建议加载期与 {@link ArmorClass#register} 同批调用。
     *
     * @param material    护甲材料（注册表内置条目或自定义实现）
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
     * <p>下界合金无独立配置项：作为远古神秘材料，其系数 =（钻石 + 金/2）÷ 2，
     * 随钻石与金的配置值动态推导；未登记的自定义材料默认 1.0（同铁套）。
     *
     * @param material 护甲材料（注册表条目实例）
     * @return 材料质量系数
     */
    public static double materialCoefficient(ArmorMaterial material) {
        Double registered = CUSTOM_MATERIAL_COEFFICIENTS.get(material);
        if (registered != null) {
            return registered; // 登记优先（含覆写内置）
        }
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        if (material == ArmorMaterials.LEATHER.value()) {
            return loaded ? Config.COMMON.leatherMassCoefficient.get() : 0.3;
        }
        if (material == ArmorMaterials.TURTLE.value()) {
            return loaded ? Config.COMMON.turtleMassCoefficient.get() : 0.6;
        }
        if (material == ArmorMaterials.CHAIN.value()) {
            return loaded ? Config.COMMON.chainMassCoefficient.get() : 1.0;
        }
        if (material == ArmorMaterials.IRON.value()) {
            return loaded ? Config.COMMON.ironMassCoefficient.get() : 1.0;
        }
        if (material == ArmorMaterials.GOLD.value()) {
            return loaded ? Config.COMMON.goldMassCoefficient.get() : 1.7;
        }
        if (material == ArmorMaterials.DIAMOND.value()) {
            return loaded ? Config.COMMON.diamondMassCoefficient.get() : 0.45;
        }
        if (material == ArmorMaterials.NETHERITE.value()) {
            // 下界合金：(钻石 + 金/2) / 2，随钻石/金配置动态推导（默认 0.65），再次 / 2 是世界观修正
            return loaded
                    ? (Config.COMMON.diamondMassCoefficient.get() + Config.COMMON.goldMassCoefficient.get() / 2) / 2
                    : (0.45 + 1.7 / 2) / 2;
        }
        return 1.0; // 未登记材料（含 ARMADILLO）默认同铁套
    }

    /**
     * 玩家质量 = 质量基数 + 最大生命值 × 质量系数 + 穿戴物品的玩家质量加成。
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

    /** 玩家最大负重质量 = 负重系数 × 玩家质量。 */
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
     *   <li>{@link ArmorItem} → floor(材料系数 × AT × 护甲值)；</li>
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
        // 未覆写时回退 ArmorItem 的原始值，避免"改了护甲值质量却不变"的脱节
        double defense = armor.getDefense();
        ArmorAttributeRules.ArmorStats stats = ArmorAttributeRules.statsOf(armor);
        if (stats != null) {
            defense = stats.armor();
        }
        ArmorMaterial material = armor.getMaterial().value();
        double mass = materialCoefficient(material)
                * ArmorClass.of(material).ArmorTypeCoefficient()
                * defense;
        return (int) Math.floor(mass);
    }

    /** 玩家当前穿戴物品的总质量（{@link #collectWornStacks} 遍历的所有槽位之和）。 */
    public static int getTotalWornMass(Player player) {
        int total = 0;
        for (ItemStack stack : collectWornStacks(player)) {
            total += getItemMass(stack);
        }
        return total;
    }

    /** 玩家穿戴物品提供的<b>玩家质量加成</b>总和。 */
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
     * 收集玩家当前穿戴的全部物品栈：护甲四槽 + 主手 + 副手。
     *
     * <p>1.21.1 的 Curios 饰品槽接入待补（软依赖）；未接入时行为等价于"未安装 Curios"。
     */
    private static List<ItemStack> collectWornStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(16);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            stacks.add(player.getItemBySlot(slot));
        }
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        return stacks;
    }

    /**
     * 负重比例 = 穿戴总质量 / 最大负重质量。
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
     * <p>本公式结果恒非负（负重比例 ≥ 0）。<b>负数击退抗性由附属模组提供</b>：
     * 属性范围默认<b>无下限</b>（{@code Config.COMMON.knockbackResistanceNoLowerLimit}），
     * 附属直接为玩家 {@code Attributes.KNOCKBACK_RESISTANCE} 添加负修饰符即可生效；
     * {@link PlayerMassEffects} 每 tick 仅更新本模组固定 ID 的修饰符，不会移除外来修饰符。
     */
    public static double getKnockbackResistance(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 1.2;
        return Math.min(1.0, getLoadRatio(player) * factor);
    }

    /** 击退抗性显示点数（0.0 ~ 100.0）= 属性值 × 100，供 tooltip 直接展示 "+X"。 */
    public static double getKnockbackResistancePoints(Player player) {
        return getKnockbackResistance(player) * 100;
    }

    /**
     * 移速减益比例 = 负重比例 × 移速减益因子（可配置 {@code speedPenaltyFactor}，默认 0.6）。
     *
     * <p>表示移动速度的减少幅度：移速 = 基础移速 × (1 - 比例)，
     * 负重比例 1（满负载）时减 60%（默认因子）；超重（比例 &gt; 1）时减益继续上升，
     * 由原版移速属性下限（0）兜底。
     */
    public static double getSpeedPenalty(Player player) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.6;
        return getLoadRatio(player) * factor;
    }

    /**
     * 预览负重比例：<b>仅该物品自身质量</b>占最大负重的比例
     * （{@code 物品质量 / 最大负重质量}）。
     *
     * <p><b>不叠加当前穿戴总质量</b>，也不做槽位替换计算。
     */
    public static double getPreviewLoadRatio(Player player, double additionalMass) {
        double max = getMaxLoadMass(player);
        return max <= 0 ? 0 : additionalMass / max;
    }

    /** 预览移速减益比例：预览负重比例 × 移速减益因子，与 {@link #getSpeedPenalty} 公式一致。 */
    public static double getPreviewSpeedPenalty(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.speedPenaltyFactor.get() : 0.6;
        return getPreviewLoadRatio(player, additionalMass) * factor;
    }

    /** 预览击退抗性显示点数（0.0 ~ 100.0），与 {@link #getKnockbackResistancePoints} 公式一致。 */
    public static double getPreviewKnockbackResistancePoints(Player player, double additionalMass) {
        boolean loaded = Config.COMMON_SPEC.isLoaded();
        double factor = loaded ? Config.COMMON.knockbackResistanceFactor.get() : 1.2;
        return Math.min(1.0, getPreviewLoadRatio(player, additionalMass) * factor) * 100;
    }
}
