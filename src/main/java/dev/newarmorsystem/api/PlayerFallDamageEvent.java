package dev.newarmorsystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 玩家摔落伤害结算事件 —— 质量系统摔落公式的结果在返回原版管线之前触发，可改写最终伤害。
 *
 * <p>本事件由 {@link FallDamage#computeDamage} 在公式
 * {@code D = (玩家质量 + 护甲质量) × g × (h − 安全高度) / 分母 × 倍率}
 * 结算完成之后派发，结算函数的返回值取 {@link #getDamage()}。
 * 没有被监听时行为与不派发完全一致（返回公式结果的向上取整值）。
 *
 * <p><b>与 Forge 原生 {@code LivingFallEvent} 的关系</b>：{@code LivingFallEvent}
 * 在公式<b>之前</b>触发，只能改下落距离与伤害倍率（两者均已进入本公式）；
 * 本事件在公式<b>之后</b>触发，改的是公式结果。顺序：
 * {@code LivingFallEvent} → 本事件 → 原版后续逻辑（摔落音效、{@code hurt}）。
 *
 * <p><b>不可取消</b>：「免除本次摔落伤害」用 {@link #setDamage(int)} 设 0 即可表达
 * （与取消语义等价，且不影响原版后续流程对 0 伤害的处理）。
 *
 * <p><b>派发范围</b>：安全高度以内（公式结果 0）同样派发 —— 监听者可以对
 * "本不该受伤的坠落"附加效果；无监听者时 0 原样返回，行为不变。
 * 仅服务端结算链路派发（{@code LivingEntity#calculateFallDamage}，由
 * {@code LivingEntityFallDamageMixin} 仅对玩家重定向；非玩家原样走原版公式，
 * 不派发本事件）。
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
    private final int originalDamage;

    /** 当前生效的摔落伤害（可写）。 */
    private int damage;

    /**
     * @param player         承受摔落的玩家
     * @param height         下落高度（已含 {@code LivingFallEvent} 的修改）
     * @param multiplier     伤害倍率
     * @param mass           参与公式的总质量（玩家质量 + 穿戴物品总质量）
     * @param safeHeight     有效安全高度（含跳跃提升加成）
     * @param originalDamage 公式结算结果（{@code damage} 初值同此）
     */
    public PlayerFallDamageEvent(Player player, float height, float multiplier,
                                 double mass, double safeHeight, int originalDamage) {
        super(player);
        this.height = height;
        this.multiplier = multiplier;
        this.mass = mass;
        this.safeHeight = safeHeight;
        this.originalDamage = originalDamage;
        this.damage = originalDamage;
    }

    /** 本事件不可取消：免除伤害请用 {@link #setDamage(int)} 设 0。 */
    @Override
    public boolean isCancelable() {
        return false;
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
    public int getOriginalDamage() {
        return this.originalDamage;
    }

    /** @return 当前生效的摔落伤害（等于 {@link #getOriginalDamage()} 时说明未被改写） */
    public int getDamage() {
        return this.damage;
    }

    /**
     * 改写最终摔落伤害。
     *
     * @param damage 新的摔落伤害（负数按 0 处理；0 = 本次不受伤）
     */
    public void setDamage(int damage) {
        this.damage = Math.max(0, damage);
    }
}
