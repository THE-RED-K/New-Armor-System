package dev.newarmorsystem.api;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 反伤比例（Damage Reflection Ratio）注册接口 —— 护甲损失耐久度时的反伤系统。
 *
 * <p><b>触发时机</b>：护甲<b>损失耐久度时</b>（而不是玩家受到伤害时），对本次伤害来源进行反伤。
 * 反伤伤害值 = 反伤比例 × 护甲损失的耐久度
 * （{@link NewCombatRules#getDurabilityLoss} 计算出的本件护甲损耗，经事件改写后
 * 按 {@code (int)} 截断的实际扣除点数，见 {@link ArmorHurtHandler#apply}）。
 *
 * <p><b>反伤目标</b>：
 * <ul>
 *   <li>默认：反伤给本次伤害来源（{@code DamageSource#getEntity()}），
 *       可为伤害来源的玩家自己（护甲掉耐久即反伤 —— 玩家自身让护甲受伤同样被反伤）。</li>
 *   <li><b>诅咒类护甲</b>：{@link #registerReflectToSelf} 开启后<b>必定反伤玩家自己</b>
 *       （穿戴者），而不是反伤目标 —— 无论伤害来源是谁、是否存在实体来源。</li>
 * </ul>
 *
 * <p><b>反伤比例构成</b>：反伤比例 = 本登记表材料比例 + <b>荆棘附魔等级 × 60%</b>
 * （每级 +60%，必定反伤、无概率判定；原版荆棘机制已被禁用并并入本系统）。
 * 材料比例默认 <b>0%</b>（当前所有原版护甲均未登记），仅靠材料时反伤为 0 ——
 * 该登记表是未来附属模组的预留接口；原版护甲附荆棘即可获得荆棘部分的反伤。
 *
 * <p><b>刻意的设计</b>：低伤害攻击可能让护甲不掉耐久（例如带耐久附魔时
 * 损耗 &lt; 1 被舍入为 0），此时无耐久损失、无反伤 —— 因此反伤天然对小怪无效。
 *
 * <p><b>取值约束</b>：反伤比例<b>无上限</b>，<b>最小值 0.00</b>（0 即无反伤）；
 * 负数视为取消注册（恢复 0%）。自我反伤开关默认 <b>false</b>，
 * 注册 false 即取消（恢复默认行为）。
 *
 * <p><b>1.20.1 → 1.21.1</b>：键类型不变（仍按护甲材料身份登记）。1.21.1 的
 * {@link ArmorMaterial} 已由枚举变为注册表条目（记录类型），物品侧持有
 * {@code Holder<ArmorMaterial>}，故调用方需经 {@link ArmorItem#getMaterial()}
 * 的 {@code value()} 取实例再查询（{@link ArmorMaterials} 的常量同样是 Holder）。
 *
 * @author THEREDK
 */
public final class DamageReflection {

    private DamageReflection() {
    }

    /** 护甲材料 → 反伤比例登记表（未登记 = 0%）。 */
    private static final Map<ArmorMaterial, Double> REFLECTION_RATIOS = new IdentityHashMap<>();

    /** 护甲材料 → 自我反伤开关登记表（未登记 = false，反伤给伤害来源）。 */
    private static final Map<ArmorMaterial, Boolean> SELF_REFLECT_FLAGS = new IdentityHashMap<>();

    /**
     * 为护甲材料登记反伤比例。
     *
     * <p>比例<b>无上限</b>、<b>最小值 0.00</b>（0 即无反伤）；负数视为取消注册。
     * 建议在加载期与 {@link ArmorClass#register} 同批调用（登记表非线程安全）。
     *
     * @param material 护甲材料（{@code ArmorMaterials.LEATHER.value()} 等注册表实例或自定义材料）
     * @param ratio    反伤比例（≥ 0 注册；&lt; 0 取消注册）
     */
    public static void register(ArmorMaterial material, double ratio) {
        if (ratio >= 0.0) {
            REFLECTION_RATIOS.put(material, ratio);
        } else {
            REFLECTION_RATIOS.remove(material);
        }
    }

    /**
     * 查询护甲材料的反伤比例；未登记返回 0.0（无反伤）。
     *
     * @param material 护甲材料
     * @return 反伤比例（恒 ≥ 0）
     */
    public static double of(ArmorMaterial material) {
        Double ratio = REFLECTION_RATIOS.get(material);
        return ratio != null ? ratio : 0.0;
    }

    /**
     * 为护甲材料登记"自我反伤"开关（用于诅咒类护甲）。
     *
     * <p>默认 <b>false</b>：反伤给本次伤害来源。开启（true）后
     * <b>必定反伤玩家自己</b>（穿戴者）—— 无论伤害来源是谁、是否存在实体来源，
     * 而不是反伤给目标。注册 false 即取消（恢复默认行为）。
     * 建议在加载期与 {@link #register} 同批调用（登记表非线程安全）。
     *
     * @param material 护甲材料
     * @param enabled  是否必定反伤玩家自己（true 开启；false 取消）
     */
    public static void registerReflectToSelf(ArmorMaterial material, boolean enabled) {
        if (enabled) {
            SELF_REFLECT_FLAGS.put(material, Boolean.TRUE);
        } else {
            SELF_REFLECT_FLAGS.remove(material);
        }
    }

    /**
     * 查询护甲材料是否开启了"自我反伤"（诅咒类）；未登记返回 false。
     *
     * @param material 护甲材料
     * @return true = 必定反伤玩家自己；false = 反伤给伤害来源
     */
    public static boolean reflectsToSelf(ArmorMaterial material) {
        return SELF_REFLECT_FLAGS.containsKey(material);
    }
}
