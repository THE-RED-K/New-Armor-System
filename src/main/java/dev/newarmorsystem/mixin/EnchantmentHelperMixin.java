package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.BrokenState;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.NewCombatRules;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 本类在 1.21.1 承担<b>两个</b>模块：<b>耐久附魔（Unbreaking）重做</b>
 * 与 <b>损坏态（broken）附魔失效</b>。
 *
 * <p><b>1.20.1 → 1.21.1 的位置迁移</b>：1.20.1 时这两个模块分居两处 ——
 * 耐久附魔重做在 {@code ItemStack#hurt(int, RandomSource, ServerPlayer)} 的 {@code @Overwrite} 里，
 * 损坏态附魔失效在本类（注入 {@code getTagEnchantmentLevel} / {@code getItemEnchantmentLevel}）。
 * 1.21.1 起 {@code ItemStack#hurt} 已被删除（耐久减免被 NeoForge 收进
 * {@link EnchantmentHelper#processDurabilityChange}），两个模块因此合并到本类。
 *
 * <h2>一、耐久附魔重做</h2>
 * <p>护甲（{@link ArmorItem}）与盾牌（{@link ShieldItem}）由「概率性免损」改为「确定性减免」
 * {@code floor(amount / (1 + unbreakingCoefficient * level))}；减免后剩余为 0 则不损耗耐久
 * （调用方 {@code hurtAndBreak} 会直接 return）。其它物品（工具、武器、弓、鞘翅等）保留原版
 * 概率性减免 —— 它们的单次损耗恒为 1，确定性公式会导致其永不磨损或减免完全失效。
 *
 * <h2>二、损坏态附魔失效</h2>
 * <p>broken 物品「失去全部效果」中的附魔一项。1.21 的附魔是<b>数据驱动</b>且
 * <b>不存在「取等级」的统一汇聚点</b>（1.20.1 可拦 {@code getTagEnchantmentLevel} 一处覆盖全部），
 * 故此处覆盖<b>四类入口</b>：
 * <ol>
 *   <li>{@code runIterationOnItem(ItemStack, EnchantmentVisitor)} —— <b>玩法效果的总入口</b>：
 *       减伤、锋利、效率、抢夺/时运、引雷、经验修补等一切附魔效果都经
 *       {@code EnchantmentHelper} 的 {@code modify*}/{@code get*} 辅助方法迭代施加，而那些方法
 *       最终都走本入口（装备路径见下一条，{@code runIterationOnEquipment} 内部逐槽位调用本条）；</li>
 *   <li>{@code runIterationOnItem(ItemStack, EquipmentSlot, LivingEntity, EnchantmentInSlotVisitor)}
 *       —— 带槽位与实体上下文的装备路径；</li>
 *   <li>{@code getItemEnchantmentLevel} —— 玩法侧单条查询（@Deprecated 入口，内部转发到
 *       NeoForge 的 {@code ItemStack#getEnchantmentLevel}）。1.21 中 vanilla 仅此一处调用它，
 *       {@code getEnchantmentLevel(Holder, LivingEntity)} 亦转发到它，故拦此处即覆盖单条玩法查询；</li>
 *   <li>{@code getTagEnchantmentLevel} —— <b>NBT 读取点</b>（NeoForge 新增的 helper，非原版方法，
 *       故必须 {@code remap = false}）；与 1.20.1 的注入点一一对应。</li>
 * </ol>
 *
 * <p><b>刻意不拦</b>（与 1.20.1 的设计约束一致）：{@code getTagEnchantments} / {@code getEnchantments} /
 * {@code getAllEnchantments} / {@code hasAnyEnchantments} —— tooltip 的附魔名与附魔光效直读数据组件，
 * <b>broken 物品保留附魔视觉，仅玩法效果失效</b>。
 *
 * <p><b>已知边界</b>：第三方模组若直接调用 NeoForge 扩展接口
 * {@code ItemStack#getEnchantmentLevel(Holder)}（{@code IItemStackExtension} 的 default 方法），
 * 不会经过本类拦截（拦截接口 default 方法的代价与风险都过高）。本模组自身的直读已另行处理
 * （见 {@link NewCombatRules#getUnbreakingLevel} / {@link NewCombatRules#getThornsLevel}）。
 *
 * <p>本类按「只做注入」的惯例保持默认优先级，不使用 {@code MixinPriorities.TAKEOVER}
 * （该常量专供整体接管方法体的 {@code @Overwrite}）。
 *
 * <p>不要在本类的原版方法上添加 {@code remap = false}（仅 {@code getTagEnchantmentLevel} 例外）。
 *
 * @author THEREDK
 * @reason 护甲/盾牌改为可配置系数的确定性减免；broken 物品失去附魔效果
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    // ===== 一、耐久附魔重做 =====

    /**
     * 接管护甲/盾牌的耐久减免：确定性公式替代原版逐点概率判定。
     *
     * @author THEREDK
     * @reason 护甲/盾牌改为确定性减免
     */
    @Inject(method = "processDurabilityChange", at = @At("HEAD"), cancellable = true)
    private static void newArmorSystem$deterministicUnbreaking(ServerLevel level, ItemStack stack, int damage,
                                                              CallbackInfoReturnable<Integer> cir) {
        if (damage <= 0) {
            return;  // 与原实现一致：非正损耗无事发生
        }
        if (!(stack.getItem() instanceof ArmorItem) && !(stack.getItem() instanceof ShieldItem)) {
            return;  // 其它物品保留原版概率性减免
        }
        int unbreakingLevel = NewCombatRules.getUnbreakingLevel(stack);
        double coefficient = Config.COMMON_SPEC.isLoaded()
                ? Config.COMMON.unbreakingCoefficient.get()
                : 1.0;
        int reduced = (int) Math.floor(damage / (1.0 + coefficient * unbreakingLevel));
        cir.setReturnValue(reduced);
    }

    // ===== 二、损坏态附魔失效 =====

    /**
     * 玩法效果总入口：broken 物品的附魔效果整体失效（附魔视觉不受影响）。
     *
     * @author THEREDK
     * @reason broken 物品失去附魔效果
     */
    @Inject(
            method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void newArmorSystem$skipBrokenItemEnchantments(ItemStack stack,
                                                                  EnchantmentHelper.EnchantmentVisitor visitor,
                                                                  CallbackInfo ci) {
        if (BrokenState.isBroken(stack)) {
            ci.cancel();
        }
    }

    /**
     * 玩法效果总入口（带槽位/实体上下文的装备路径）。
     *
     * @author THEREDK
     * @reason broken 物品失去附魔效果
     */
    @Inject(
            method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void newArmorSystem$skipBrokenEquipmentEnchantments(ItemStack stack, EquipmentSlot slot,
                                                                       LivingEntity entity,
                                                                       EnchantmentHelper.EnchantmentInSlotVisitor visitor,
                                                                       CallbackInfo ci) {
        if (BrokenState.isBroken(stack)) {
            ci.cancel();
        }
    }

    /**
     * 玩法侧单条附魔查询：broken 物品恒返回 0。
     *
     * <p>{@code getEnchantmentLevel(Holder, LivingEntity)} 与各处的单条查询都转发到本方法。
     *
     * @author THEREDK
     * @reason broken 物品失去附魔效果
     */
    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void newArmorSystem$disableBrokenGameplayEnchants(Holder<Enchantment> enchantment, ItemStack stack,
                                                                    CallbackInfoReturnable<Integer> cir) {
        if (BrokenState.isBroken(stack)) {
            cir.setReturnValue(0);
        }
    }

    /**
     * NBT 读取点：broken 物品恒返回 0（与 1.20.1 的主注入点一一对应）。
     *
     * <p>{@code getTagEnchantmentLevel} 是 NeoForge 新增的 helper（非原版方法，无混淆映射），
     * 故必须 {@code remap = false}。
     *
     * <p>注意：本方法与 {@code getItemEnchantmentLevel} 的<b>描述符完全相同</b>
     * （{@code (Holder;ItemStack)I}），因此只能用<b>方法名</b>作选择器，不可使用完整描述符。
     *
     * @author THEREDK
     * @reason broken 物品失去附魔效果
     */
    @Inject(method = "getTagEnchantmentLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void newArmorSystem$disableBrokenTagEnchants(Holder<Enchantment> enchantment, ItemStack stack,
                                                               CallbackInfoReturnable<Integer> cir) {
        if (BrokenState.isBroken(stack)) {
            cir.setReturnValue(0);
        }
    }
}
