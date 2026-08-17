package dev.newarmorsystem.api;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 护甲属性（护甲值 / 盔甲韧性）覆写规则 —— 全模组统一改写入口。
 *
 * <p>允许附属模组覆写<b>任意护甲</b>（原版或其它模组）的护甲值与盔甲韧性，
 * 包括各部位互不相同的值。覆写后物品在属性面板、玩家实际属性计算
 * （减伤公式等）与 tooltip 中显示/生效的均为新值 —— 三者共用
 * {@code ItemStack.getAttributeModifiers} 同一数据源，故显示与效果天然一致
 * （具体改写见 {@code ArmorAttributeMixin}）。
 *
 * <p>三种登记粒度，按物品登记优先：
 * <ul>
 *   <li><b>按物品</b>（{@link #register(Item, double, double)}）：精确到单件护甲，
 *       各部位互不影响（如只改铁头盔不改铁胸甲）。非 {@link ArmorItem} 的
 *       自定义护甲物品也可登记，但需其自身已提供护甲属性条目（见下方"生效条件"）。</li>
 *   <li><b>按物品 × 槽位（强制）</b>（{@link #register(Item, EquipmentSlot, double, double)}）：
 *       显式指定装备槽位后<b>无条件写入</b>护甲值/韧性 —— 即使该物品不是
 *       {@link ArmorItem}、原本不提供任何护甲属性条目（如自定义穿戴物、饰品），
 *       也会在该槽位获得护甲值/韧性；是最高的强制覆盖手段。</li>
 *   <li><b>按材料 × 部位</b>（{@link #register(ArmorMaterial, ArmorItem.Type, double, double)}）：
 *       同一材料按头盔/胸甲/护腿/靴子分别配值，自动应用到该材料下全部护甲物品；
 *       未登记的部位保持原版值。另有四部位统一登记的
 *       {@link #registerMaterial(ArmorMaterial, double, double)}。</li>
 * </ul>
 *
 * <p>生效条件（与 {@code ItemStack.getAttributeModifiers(EquipmentSlot)} 行为一致）：
 * <ul>
 *   <li><b>强制登记</b>（物品 × 槽位）：无视下述条件，直接在该槽位写入/接管属性；</li>
 *   <li>登记物品为 {@link ArmorItem} 时，仅在其<b>对应装备槽位</b>生效
 *       （与原版护甲只在装备槽提供属性一致）；</li>
 *   <li>登记物品非 {@link ArmorItem} 时，仅当该物品在目标槽位<b>原本就提供护甲值
 *       或韧性条目</b>才改写（即 map 中已含 ARMOR / ARMOR_TOUGHNESS 修饰符），
 *       否则无法识别其护甲身份、不添加条目 —— 需要无条件添加请用强制登记。</li>
 * </ul>
 *
 * <p>数值语义：
 * <ul>
 *   <li>{@code armor} 与 {@code toughness} 均允许 0：0 表示该护甲不再提供该项属性；
 *       两者都为 0 表示完全移除该护甲的护甲值/韧性条目（不再显示 +X 属性行）。</li>
 *   <li>负值非法，构造 {@link ArmorStats} 时抛 {@link IllegalArgumentException}；
 *       取消登记请用 {@link #remove(Item)} / {@link #remove(ArmorMaterial, ArmorItem.Type)}
 *       / {@link #removeMaterial(ArmorMaterial)}。</li>
 * </ul>
 *
 * <p>覆写即"接管"：登记后该物品在该槽位的 ARMOR / ARMOR_TOUGHNESS 条目被整体替换
 * （保留原条目 UUID，未提供时回退原版标准 UUID），其它属性条目（如击退抗性、
 * 模组自定义属性）不受影响。登记表<b>非线程安全</b>，建议加载期
 * （与 {@link DamageReflection#register} 同批）调用。
 *
 * @author THEREDK
 */
public final class  ArmorAttributeRules {

    private ArmorAttributeRules() {
    }

    /**
     * 单件护甲的护甲属性（护甲值 + 盔甲韧性）。
     *
     * @param armor     护甲值（≥ 0；0 = 不提供护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 不提供盔甲韧性）
     */
    public record ArmorStats(double armor, double toughness) {

        public ArmorStats {
            if (armor < 0) {
                throw new IllegalArgumentException("armor must be >= 0, got " + armor);
            }
            if (toughness < 0) {
                throw new IllegalArgumentException("toughness must be >= 0, got " + toughness);
            }
        }
    }

    /** 按物品（精确到单件护甲）的覆写表，优先级高于材料表。 */
    private static final Map<Item, ArmorStats> ITEM_STATS = new IdentityHashMap<>();

    /** 按物品 × 槽位的<b>强制</b>覆写表（最高优先级；适用于非 {@link ArmorItem} 物品）。 */
    private static final Map<Item, EnumMap<EquipmentSlot, ArmorStats>> FORCED_SLOT_STATS = new IdentityHashMap<>();

    /** 按材料 × 部位的覆写表（同一材料不同部位可配不同值）。 */
    private static final Map<ArmorMaterial, EnumMap<ArmorItem.Type, ArmorStats>> MATERIAL_STATS = new IdentityHashMap<>();

    /**
     * 按<b>具体物品</b>登记护甲属性（精确到单件护甲，最高优先级，覆盖材料表）。
     *
     * @param item      护甲物品（原版/模组均可）
     * @param armor     护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    public static void register(Item item, double armor, double toughness) {
        register(item, new ArmorStats(armor, toughness));
    }

    /** 按具体物品登记的 {@link ArmorStats} 版本。 */
    public static void register(Item item, ArmorStats stats) {
        ITEM_STATS.put(Objects.requireNonNull(item, "item"), Objects.requireNonNull(stats, "stats"));
    }

    /**
     * 按<b>物品 × 槽位（强制）</b>登记护甲属性：无条件在该槽位写入护甲值/韧性，
     * 即使物品不是 {@link ArmorItem}、原本不提供任何护甲属性条目（如自定义穿戴物、
     * 饰品）也生效。优先级高于按物品与按材料登记，且无视"原生效条件"。
     *
     * <p>注意：强制登记仅改写 {@code ItemStack.getAttributeModifiers(slot)} 的返回值，
     * 不改变物品的<b>可装备性</b>（能否放入槽位仍取决于物品自身实现/槽位逻辑），
     * 也不影响其它槽位（未登记的槽位保持原版行为）。
     *
     * @param item      任意物品（原版/模组、护甲/非护甲均可）
     * @param slot      生效的装备槽位（如 {@code EquipmentSlot.HEAD}、{@code EquipmentSlot.CHEST}）
     * @param armor     护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    public static void register(Item item, EquipmentSlot slot, double armor, double toughness) {
        register(item, slot, new ArmorStats(armor, toughness));
    }

    /** 按物品 × 槽位（强制）登记的 {@link ArmorStats} 版本。 */
    public static void register(Item item, EquipmentSlot slot, ArmorStats stats) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(stats, "stats");
        FORCED_SLOT_STATS.computeIfAbsent(item, i -> new EnumMap<>(EquipmentSlot.class)).put(slot, stats);
    }

    /**
     * 按<b>材料 × 部位</b>登记护甲属性：自动应用到该材料下全部护甲物品的对应部位。
     * 未登记的部位保持原版值；多次登记同部位覆盖。
     *
     * @param material 护甲材料（原版 {@code ArmorMaterials} 枚举值或自定义实现）
     * @param type     部位（头盔 / 胸甲 / 护腿 / 靴子）
     * @param armor    护甲值（≥ 0；0 = 移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 移除盔甲韧性）
     */
    public static void register(ArmorMaterial material, ArmorItem.Type type, double armor, double toughness) {
        register(material, type, new ArmorStats(armor, toughness));
    }

    /** 按材料 × 部位登记的 {@link ArmorStats} 版本。 */
    public static void register(ArmorMaterial material, ArmorItem.Type type, ArmorStats stats) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(stats, "stats");
        MATERIAL_STATS.computeIfAbsent(material, m -> new EnumMap<>(ArmorItem.Type.class)).put(type, stats);
    }

    /**
     * 按<b>材料</b>统一登记护甲属性：四个部位（头盔/胸甲/护腿/靴子）应用相同值。
     * 需要各部位不同值请改用 {@link #register(ArmorMaterial, ArmorItem.Type, double, double)}。
     */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包调用
    public static void registerMaterial(ArmorMaterial material, double armor, double toughness) {
        registerMaterial(material, new ArmorStats(armor, toughness));
    }

    /** 按材料统一登记的 {@link ArmorStats} 版本。 */
    public static void registerMaterial(ArmorMaterial material, ArmorStats stats) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(stats, "stats");
        EnumMap<ArmorItem.Type, ArmorStats> map =
                MATERIAL_STATS.computeIfAbsent(material, m -> new EnumMap<>(ArmorItem.Type.class));
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            map.put(type, stats);
        }
    }

    /** 取消按物品登记的护甲属性（恢复原版值）。 */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包调用
    public static void remove(Item item) {
        ITEM_STATS.remove(item);
    }

    /** 取消按物品 × 槽位（强制）登记的护甲属性（该槽位恢复原版行为）。 */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包调用
    public static void remove(Item item, EquipmentSlot slot) {
        EnumMap<EquipmentSlot, ArmorStats> perSlot = FORCED_SLOT_STATS.get(item);
        if (perSlot != null) {
            perSlot.remove(slot);
        }
    }

    /** 按材料 × 部位登记的护甲属性（该部位恢复原版值）。 */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包调用
    public static void remove(ArmorMaterial material, ArmorItem.Type type) {
        EnumMap<ArmorItem.Type, ArmorStats> map = MATERIAL_STATS.get(material);
        if (map != null) {
            map.remove(type);
        }
    }

    /** 取消按材料登记的全部护甲属性（该材料全部部位恢复原版值）。 */
    @SuppressWarnings("unused") // 公开 API：供第三方模组/整合包调用
    public static void removeMaterial(ArmorMaterial material) {
        MATERIAL_STATS.remove(material);
    }

    /**
     * 查询物品在<b>指定槽位</b>的生效护甲属性（供 Mixin 按槽位解析）：
     * 优先按物品 × 槽位（强制），其次按物品，最后按材料 × 部位推导（仅限 {@link ArmorItem}）。
     * 未登记任何覆写时返回 {@code null}（保持原版值）。
     *
     * @param item 物品
     * @param slot 装备槽位；为 {@code null} 时退化为不含强制表的查询
     * @return 生效的护甲属性；无覆写返回 {@code null}
     */
    @Nullable
    public static ArmorStats statsOf(Item item, @Nullable EquipmentSlot slot) {
        if (item == null) {
            return null;
        }
        if (slot != null) {
            EnumMap<EquipmentSlot, ArmorStats> perSlot = FORCED_SLOT_STATS.get(item);
            if (perSlot != null) {
                ArmorStats forced = perSlot.get(slot);
                if (forced != null) {
                    return forced;
                }
            }
        }
        return statsOf(item);
    }

    /**
     * 查询物品的<b>生效</b>护甲属性：按物品登记优先，其次按材料 × 部位推导
     * （仅限 {@link ArmorItem}）。未登记任何覆写时返回 {@code null}（保持原版值）。
     *
     * @param item 护甲物品
     * @return 生效的护甲属性；无覆写返回 {@code null}
     */
    @Nullable
    public static ArmorStats statsOf(Item item) {
        if (item == null) {
            return null;
        }
        ArmorStats itemStats = ITEM_STATS.get(item);
        if (itemStats != null) {
            return itemStats;
        }
        if (item instanceof ArmorItem armorItem) {
            EnumMap<ArmorItem.Type, ArmorStats> perType = MATERIAL_STATS.get(armorItem.getMaterial());
            if (perType != null) {
                return perType.get(armorItem.getType());
            }
        }
        return null;
    }

    /**
     * 判断该物品是否在该槽位存在<b>强制</b>登记（物品 × 槽位表）。
     * Mixin 据此放宽生效条件：强制登记无视"物品原本是否提供护甲属性"。
     *
     * @param item 物品
     * @param slot 装备槽位
     * @return 是否为强制登记
     */
    public static boolean isForced(Item item, @Nullable EquipmentSlot slot) {
        if (item == null || slot == null) {
            return false;
        }
        EnumMap<EquipmentSlot, ArmorStats> perSlot = FORCED_SLOT_STATS.get(item);
        return perSlot != null && perSlot.containsKey(slot);
    }
}
