package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.MixinPriorities;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 重做荆棘附魔（Thorns）：移除原版机制，并入本模组反伤系统。
 *
 * <p><b>1.20.1 → 1.21.1 的机制变更</b>：1.20.1 的荆棘是硬编码的
 * {@code ThornsEnchantment#doPostHurt}（按概率 {@code 0.15 × 等级} 触发 1~4 点反伤，
 * 并对随机一件荆棘护甲额外损耗 2 点耐久），故当时用 {@code @Overwrite} 把该方法清空即可整体禁用。
 * 1.21.1 起<b>荆棘已完全数据驱动</b>（{@code ThornsEnchantment} 类不复存在），
 * 原版定义见 {@code data/minecraft/enchantment/thorns.json} 的
 * {@code minecraft:post_attack} 效果（{@code all_of[damage_entity(thorns, 1~5), damage_item(2.0)]}，
 * 受 {@code random_chance} 约束）。对应的执行入口是本方法
 * —— {@link Enchantment#doPostAttack(ServerLevel, int, EnchantedItemInUse, EnchantmentTarget, Entity, DamageSource)}
 * （按 {@code EnchantmentTarget} 遍历该附魔的 {@code POST_ATTACK} 效果），
 * 它正是 1.20.1 {@code doPostHurt} 的对等物：在此处整体取消，
 * 反伤与额外耐久损耗<b>一并停用</b>，与 1.20.1 的空实现语义一致。
 *
 * <p>本模组的替代方案：反伤在护甲<b>损失耐久时</b>统一结算
 * （见 {@code ArmorHurtHandler#apply}），反伤比例 = 材料反伤比例 + 荆棘等级 × 60%，
 * <b>必定反伤</b>（无概率判定）。
 *
 * <p><b>为何不用 {@code @Overwrite}</b>：目标方法是<b>所有附魔共用</b>的通用入口
 * （每次攻击结算都会走），覆写需完整复刻其遍历体，风险与维护成本都高；
 * 改用 {@code @Inject} + {@code ci.cancel()} 只在命中荆棘时提前返回，
 * 其余附魔的原版行为分毫不动。
 *
 * <p><b>为何按注册表键判定而非身份比较</b>：NeoForge 会把原版附魔<b>以代码构建</b>并注册进
 * 内置注册表（见 {@code Enchantments} 的静态构建），而运行期使用的是数据包加载出来的
 * <b>另一批实例</b>，实例身份不可靠。故此处用
 * {@code Registry#getResourceKey} 取实例自身的键与 {@code minecraft:thorns} 比对 ——
 * 数据包重载后依然成立，不会有缓存失效问题。
 *
 * @author THEREDK
 * @reason 原版荆棘概率反伤与额外耐久损耗并入模组反伤系统
 */
// 接管型 mixin：低优先级先写入方法体，使其它模组的注入仍能落在接管后的方法上（机制与取值见 MixinPriorities）
@Mixin(value = Enchantment.class, priority = MixinPriorities.TAKEOVER)
public abstract class ThornsEnchantmentMixin {

    /**
     * 命中 {@code minecraft:thorns} 时整体取消其 {@code POST_ATTACK} 效果
     * （概率反伤 + 额外耐久损耗），其余附魔原样放行。
     */
    @Inject(
            method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void newArmorSystem$disableVanillaThorns(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                                     EnchantmentTarget target, Entity entity, DamageSource damageSource,
                                                     CallbackInfo ci) {
        if (level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getResourceKey((Enchantment) (Object) this)
                .map(Enchantments.THORNS::equals)
                .orElse(false)) {
            ci.cancel();
        }
    }
}
