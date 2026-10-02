package dev.newarmorsystem.api;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家摔落伤害结算事件 —— 质量系统摔落公式的结果在返回原版管线之前触发，可改写最终伤害。
 *
 * <p>本事件由 {@link FallDamage#computeDamage} 在公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 分母 × 倍率}
 * 结算完成之后派发，结算函数的返回值取 {@link #getDamage()}。
 * 没有被监听时行为与不派发完全一致（返回公式结果的<b>半格取整值</b>）。
 *
 * <p><b>伤害是 double</b>：MC 的伤害管线以 {@code float} 结算，故本事件保留公式的
 * <b>半格</b>精度（如 3.5），不再像早期版本那样整形为 int —— 这样"总质量"半格化
 * 带来的精度才不会在摔落伤害的出口处被抹掉。
 *
 * <p><b>与 NeoForge 原生 {@code LivingFallEvent} 的关系</b>：{@code LivingFallEvent}
 * 在公式<b>之前</b>触发，只能改下落距离与伤害倍率（两者均已进入本公式）；
 * 本事件在公式<b>之后</b>触发，改的是公式结果。顺序：
 * {@code LivingFallEvent} → 原版摔落音效 → 本事件 → {@code hurt}。
 *
 * <p><b>不可取消</b>：「免除本次摔落伤害」用 {@link #setDamage(double)} 设 0 即可表达
 * —— 伤害为 0 时 {@code hurt} 不造成任何伤害。注意原版摔落音效在本事件<b>之前</b>播放
 * （原版先播音效再结算伤害），因此设 0 不会撤销音效。
 * 本事件不实现可取消接口：NeoForge 1.21 的 {@code PlayerEvent} 不再提供
 * {@code isCancelable()}（可取消语义改由 {@code ICancellableEvent} 表达），
 * 故这里也没有可覆写的取消方法（1.20.1 侧仍有 {@code isCancelable()} 覆写）。
 *
 * <p><b>派发范围</b>：仅服务端结算链路派发（{@code LivingEntity#causeFallDamage}，
 * 由 {@code LivingEntityFallDamageMixin} 仅对玩家接管；非玩家原样走原版公式，不派发本事件）。
 * 安全高度以内的坠落不会进入原版 {@code i > 0} 分支，故不派发本事件
 * （与"越不过安全高度的坠落本就无事发生"一致）。
 *
 * @author THEREDK
 */
public class PlayerFallDamageEvent extends PlayerEvent {

    /** 下落高度（格），原版 fallDistance（已含 {@code LivingFallEvent} 的修改）。 */
    private final float height;

    /** 伤害倍率（原版 {@code calculateFallDamage} 的 damageMultiplier 参数，通常为 1.0）。 */
    private final float multiplier;

    /** 参与公式的总质量：玩家质量 + 穿戴物品总质量。 */
    private final double mass;

    /** 有效安全高度（格，含跳跃提升的乘算加成）。 */
    private final double safeHeight;

    /** 公式结算结果（未派发事件时的返回值）。 */
    private final double originalDamage;

    /** 当前生效的摔落伤害（可写）。 */
    private double damage;

    /**
     * @param player         承受摔落的玩家
     * @param height         下落高度（已含 {@code LivingFallEvent} 的修改）
     * @param multiplier     伤害倍率
     * @param mass           参与公式的总质量（玩家质量 + 穿戴物品总质量）
     * @param safeHeight     有效安全高度（含跳跃提升加成）
     * @param originalDamage 公式结算结果（{@code damage} 初值同此）
     */
    public PlayerFallDamageEvent(Player player, float height, float multiplier,
                                 double mass, double safeHeight, double originalDamage) {
        super(player);
        this.height = height;
        this.multiplier = multiplier;
        this.mass = mass;
        this.safeHeight = safeHeight;
        this.originalDamage = originalDamage;
        this.damage = originalDamage;
    }

    /** @return 下落高度（格，已含 {@code LivingFallEvent} 的修改） */
    public float getHeight() {
        return this.height;
    }

    /** @return 伤害倍率 */
    public float getMultiplier() {
        return this.multiplier;
    }

    /** @return 参与公式的总质量（玩家质量 + 穿戴物品总质量） */
    public double getMass() {
        return this.mass;
    }

    /** @return 有效安全高度（格，含跳跃提升的乘算加成） */
    public double getSafeHeight() {
        return this.safeHeight;
    }

    /** @return 公式结算结果（未派发事件时的返回值） */
    public double getOriginalDamage() {
        return this.originalDamage;
    }

    /** @return 当前生效的摔落伤害（等于 {@link #getOriginalDamage()} 时说明未被改写） */
    public double getDamage() {
        return this.damage;
    }

    /**
     * 改写最终摔落伤害。
     *
     * @param damage 新的摔落伤害（负数按 0 处理；0 = 本次不造成伤害）
     */
    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
    }
}
