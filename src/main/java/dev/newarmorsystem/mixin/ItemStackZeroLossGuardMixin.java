package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.MixinPriorities;
import dev.newarmorsystem.api.NewCombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R1 守卫 —— 本模组的「确定性耐久附魔重做」把本次损耗压到 0 时，强制
 * {@link ItemStack#hurt(int, RandomSource, ServerPlayer)} 返回 {@code false}：
 * 不掉耐久，也不触发"物品损坏"语义。
 *
 * <p><b>为什么必须单独成一个 mixin 类</b>：Mixin 的优先级是 <b>{@code @Mixin} 类级属性</b>，
 * 单个注入器无法单独提升。本守卫要抢在一切第三方对 {@code hurt} 返回值的改写之前定案，
 * 因此独立成类并配 {@link MixinPriorities#FINAL}（{@code Integer.MAX_VALUE}，最后应用）——
 * 与 {@code ItemStackMixin}（默认 1000，负责损耗换算）形成"<b>同级不同责</b>"的一对：
 * 同靶同类，各管一件事。
 *
 * <p><b>为什么用取消式注入而不是 {@code @ModifyReturnValue}</b>：
 * <ul>
 *   <li>{@code @ModifyReturnValue} 是"包装返回值"，优先级不够高时会被第三方同点包装盖掉
 *       ⇒ 守卫失效、broken 护甲受击又会被误判损坏；</li>
 *   <li>本类在 <b>HEAD</b> 处 {@code setReturnValue(false) + cancel()} ⇒ <b>原版方法体根本不执行</b>
 *       ⇒ 不存在任何 RETURN 注入点可被覆盖 ⇒ 不变量与优先级解耦，MAX_VALUE 只是保证
 *       "在其它 HEAD 取消式注入之前先手"。</li>
 * </ul>
 *
 * <p><b>判据</b>（与 {@code ItemStackMixin} 共用同一公式，单一事实来源）：
 * {@code pAmount > 0}（{@code <= 0} 是调用方给的零损耗，交回原版）且适用确定性公式
 * （{@link NewCombatRules#deterministicUnbreakingApplies}）且公式结果 ≤ 0。
 *
 * <p><b>这个守卫防的是什么</b>：原版只在 {@code pAmount <= 0} 时提前返回 {@code false}；
 * 被公式压到 0 后会继续走到尾部 {@code return damageValue >= maxDamage} —— 对耐久已封顶
 * （broken）的护甲这会返回 {@code true}，进而让 {@code hurtAndBreak} 执行
 * {@code onBroken.accept(entity)}（ITEM_BREAK 音效 / {@link dev.newarmorsystem.api.ItemBrokenEvent}）
 * 与 {@code shrink(1)}（{@code broken_state} 开启时会被 {@code ItemStackMixin} 的
 * {@code newArmorSystem$onBreak} 拦下、物品不消失，但音效与事件已经误发；开关关闭时物品更会被直接销毁）。
 * 旧 {@code @Overwrite} 在 {@code pAmount <= 0} 时提前返回，天然没有这个问题 —— 本类把它补回来。
 *
 * @author THEREDK
 */
@Mixin(value = ItemStack.class, priority = MixinPriorities.FINAL)
public abstract class ItemStackZeroLossGuardMixin {

    /**
     * @param pAmount 损耗前的原始伤害（原参数）
     * @param pRandom 随机源（原参数，未使用）
     * @param pUser   造成损耗的玩家（原参数，未使用）
     * @param cir     取消回调（{@code setReturnValue(false)} 即"不掉耐久、不损坏"）
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$suppressZeroLossBreak(int pAmount, RandomSource pRandom,
                                                      @Nullable ServerPlayer pUser,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (pAmount <= 0
                || !NewCombatRules.deterministicUnbreakingApplies((ItemStack) (Object) this)) {
            return;
        }
        if (NewCombatRules.applyDeterministicUnbreaking(pAmount, (ItemStack) (Object) this) <= 0) {
            cir.setReturnValue(false);
        }
    }
}
