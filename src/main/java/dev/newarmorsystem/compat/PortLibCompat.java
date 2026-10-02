package dev.newarmorsystem.compat;

import java.util.Map;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;

/**
 * 1.21「摔落系」属性的 1.20.1 移植兼容层（{@code portlib} / MesdagPortLib 等 backport 模组）。
 *
 * <p><b>背景</b>：1.20.1 原版没有 {@code fall_damage_multiplier} 与 {@code safe_fall_distance}
 * 这两个属性（1.21 才有），移植库把它们注册进 1.20.1 并替换 {@code calculateFallDamage} 里的表达式
 * 来"应用"它们。本模组通过 {@code @ModifyArg} 接管同一表达式（见
 * {@code LivingEntityFallDamageMixin}），两者叠加会启动崩溃 —— 因此在整合包里应把
 * <b>移植库自己的 mixin 禁用</b>（黑名单）。
 *
 * <p><b>禁用 mixin 不会影响属性注册</b>：注册发生在移植库主类（属性对象是静态字段，
 * 经 {@code Supplier} 惰性求值），与 mixin 无关 ⇒ 属性照旧存在、其它模组（饰品等）写入的
 * 属性修饰符照旧保留，缺的只是"读它并应用"这一步 —— <b>本模组正好补上这一步</b>，
 * 于是 {@link #fallDamageMultiplier(Player)} / {@link #safeFallDistance(Player, double)}
 * 成为唯一应用者，既不会"乘两次"也不会"被覆盖"。
 *
 * <p><b>为什么按注册表路径查，而不是直接引用移植库的类</b>：
 * <ol>
 *   <li>直接引用需要编译期依赖（本模组刻意不引入，避免与移植库版本强绑定）；</li>
 *   <li>按 {@code ResourceLocation#getPath()} 在整个属性注册表里查找，
 *       <b>与命名空间无关</b>（{@code portlib}/{@code mesdagportlib}/任何 backport 都能命中），
 *       也不需要"仅在 mod 加载时才触碰某个 holder 类"这种脆弱的类加载隔离；</li>
 *   <li>查找结果缓存，游戏运行期注册表已冻结，开销可忽略。</li>
 * </ol>
 *
 * <p><b>缺省行为</b>：属性不存在（未装移植库）时返回默认值（倍率 {@code 1.0}、
 * 安全高度由调用方给出原版 3 格），故本层在纯净环境下<b>完全透明</b>，不影响任何原有数值。
 * 本类<b>只读不写</b>，不会给玩家挂任何修饰符。
 *
 * <p><b>1.21.1 分支无需本类</b>：那边两个属性都是原版属性，{@code FallDamage} 直接读原版即可。
 *
 * @author THEREDK
 */
public final class PortLibCompat {

    /** 1.21 属性 {@code minecraft:fall_damage_multiplier} 的路径（默认 1.0，0 = 免疫摔落伤害）。 */
    private static final String FALL_DAMAGE_MULTIPLIER_PATH = "fall_damage_multiplier";

    /** 1.21 属性 {@code minecraft:safe_fall_distance} 的路径（单位：格，原版默认 3.0）。 */
    private static final String SAFE_FALL_DISTANCE_PATH = "safe_fall_distance";

    /** 注册表查找是否已完成（注册表在加载后冻结，查一次即可）。 */
    private static boolean resolved;

    /** 移植库注册的摔落伤害倍率属性；{@code null} = 未装移植库。 */
    private static Attribute fallDamageMultiplier;

    /** 移植库注册的安全下落距离属性；{@code null} = 未装移植库。 */
    private static Attribute safeFallDistance;

    /** 两个移植属性的完整 id（{@code namespace:path}），仅用于启动日志核对。 */
    private static ResourceLocation fallDamageMultiplierId;
    private static ResourceLocation safeFallDistanceId;

    private static final Logger LOGGER = LogUtils.getLogger();

    private PortLibCompat() {
    }

    /**
     * 在属性注册表里按<b>路径</b>查找两个移植属性（与命名空间无关），只执行一次。
     *
     * <p>遍历的是冻结后的注册表（{@link BuiltInRegistries#ATTRIBUTE}），约几十个条目，
     * 且结果缓存，故按格读取属性值的路径上无额外开销。
     */
    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (Map.Entry<ResourceKey<Attribute>, Attribute> entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
            String path = entry.getKey().location().getPath();
            if (fallDamageMultiplier == null && FALL_DAMAGE_MULTIPLIER_PATH.equals(path)) {
                fallDamageMultiplier = entry.getValue();
                fallDamageMultiplierId = entry.getKey().location();
            } else if (safeFallDistance == null && SAFE_FALL_DISTANCE_PATH.equals(path)) {
                safeFallDistance = entry.getValue();
                safeFallDistanceId = entry.getKey().location();
            }
        }
        // 命中时打一条 info（每次启动至多一条）：既便于确认"属性仍在、只有应用被禁用"这一前提，
        // 也便于整合包核对属性 id（查找与命名空间无关，portlib / 其它 backport 都会命中）。
        if (isPresent()) {
            LOGGER.info("[NewArmorSystem] detected backported 1.21 fall attributes: fall_damage_multiplier={}, "
                            + "safe_fall_distance={}. NewArmorSystem applies them inside its fall damage formula; "
                            + "keep the backport mod's LivingEntity mixin disabled (blacklist) so they are not "
                            + "applied twice.",
                    fallDamageMultiplierId, safeFallDistanceId);
        }
    }

    /**
     * 是否检测到移植库注册的摔落系属性（供调试/文档说明；不影响任何计算）。
     *
     * @return 两个属性中至少检测到一个
     */
    public static boolean isPresent() {
        resolve();
        return fallDamageMultiplier != null || safeFallDistance != null;
    }

    /**
     * 移植库的摔落伤害倍率（<b>乘性</b>）。
     *
     * <p>取属性<b>总值</b>（含其它模组写入的修饰符），故饰品/药水对摔落伤害的调整照旧生效。
     * 0 = 免疫（本模组公式里相乘：{@code 0 × x = 0}，与 NAS 自身属性的 0 语义一致）。
     *
     * @param player 承受摔落伤害的玩家
     * @return 属性缺失或未挂载时为 {@code 1.0}（完全透明）
     */
    public static double fallDamageMultiplier(Player player) {
        resolve();
        if (fallDamageMultiplier == null) {
            return 1.0D;
        }
        AttributeInstance instance = player.getAttribute(fallDamageMultiplier);
        return instance == null ? 1.0D : instance.getValue();
    }

    /**
     * 移植库的安全下落距离（格）。
     *
     * <p>仅在本模组配置 {@code fallSafeHeight = -1}（<b>跟随平台</b>）时被采用 ——
     * 此时"显式覆写"语义让位给平台：移植属性存在则用它的值（默认 3.0 = 原版安全高度，
     * 气球/马蹄铁一类饰品加成随之恢复），不存在则回退原版硬编码值。
     *
     * @param player   玩家
     * @param fallback 属性缺失或未挂载时的回退值（原版安全高度）
     * @return 安全下落距离（格）
     */
    public static double safeFallDistance(Player player, double fallback) {
        resolve();
        if (safeFallDistance == null) {
            return fallback;
        }
        AttributeInstance instance = player.getAttribute(safeFallDistance);
        return instance == null ? fallback : instance.getValue();
    }
}
