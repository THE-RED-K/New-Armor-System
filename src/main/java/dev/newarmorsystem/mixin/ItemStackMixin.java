package dev.newarmorsystem.mixin;


import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import dev.newarmorsystem.api.BrokenState;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.ItemBrokenEvent;
import dev.newarmorsystem.api.ItemRepairedEvent;
import dev.newarmorsystem.api.MixinPriorities;
import dev.newarmorsystem.api.PlayerMass;
import dev.newarmorsystem.api.PlayerMassEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * 按物品类型替换 {@link ItemStack#hurt(int, RandomSource, ServerPlayer)}
 * 中的耐久附魔（Unbreaking）减免逻辑。
 * <p>
 * 护甲（{@link ArmorItem}）与盾牌（{@link ShieldItem}）：
 * 原版按 {@code 1/(level+1)} 的概率判定是否真实扣除（随机减免），
 * 本模组改为按 {@code floor(amount / (1 + unbreakingCoefficient * level))} 确定性扣除，
 * 去除随机性；系数见 {@link Config#COMMON} 的 {@code unbreakingCoefficient}，
 * 低伤害可能完全免损。
 * <p>
 * 其它物品（工具、武器、弓、鞘翅等）：单次损耗恒为 1，
 * 确定性公式会导致其永不磨损或减免完全无效，故保留原版概率性减免。
 * <p>
 * 注意：本模组使用 {@code @Overwrite} 直接覆盖原方法体，
 * 因此与其它同样修改耐久附魔判定的 Mod 不兼容 —— 这是本模组的设计定位。
 * <p>
 * 不要在此处添加 {@code remap = false}，否则发布 jar（运行时方法名为 SRG 名）会失效。
 */
// 接管型 mixin：低优先级先写入 hurt 方法体，使其它模组的注入（如 @ModifyVariable method="hurt"）
// 仍能落在接管后的方法上（机制与取值见 MixinPriorities）
@Mixin(value = ItemStack.class, priority = MixinPriorities.TAKEOVER)
public abstract class ItemStackMixin {

    @Shadow
    public abstract boolean isDamageableItem();

    @Shadow
    public abstract int getDamageValue();

    @Shadow
    public abstract int getMaxDamage();

    @Shadow
    public abstract void setDamageValue(int pDamageValue);

    @Shadow
    public abstract Item getItem();

    /**
     * 原版实现：扣除耐久前按 {@code pRandom.nextInt(level + 1) > 0} 逐点概率减免（Unbreaking 附魔），
     * 每次损耗结果不可预测。
     * <p>
     * 本模组按物品类型分流：护甲（{@link ArmorItem}）与盾牌（{@link ShieldItem}）
     * 使用确定性公式 {@code floor(pAmount / (1 + unbreakingCoefficient * level))}，
     * 减免后剩余为 0 则直接返回 {@code false}（不掉耐久、不损坏）；
     * 其它物品保留原版概率性减免循环，其余逻辑与目标方法体保持一致。
     *
     * @author THEREDK
     * @reason 护甲/盾牌改为可配置系数的确定性减免，其余物品保留原版概率减免
     */
    @Overwrite
    public boolean hurt(int pAmount, RandomSource pRandom, @Nullable ServerPlayer pUser) {
        if (!this.isDamageableItem()) {
            return false;
        } else {
            if (pAmount > 0) {
                int UnbreakingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, (ItemStack)(Object) this);
                if (this.getItem() instanceof ArmorItem || this.getItem() instanceof ShieldItem) {
                    if (Config.COMMON_SPEC.isLoaded()) {
                        pAmount = (int) Math.floor(pAmount / (1.0D + Config.COMMON.unbreakingCoefficient.get() * UnbreakingLevel));
                    } else {
                        pAmount = (int) Math.floor(pAmount / (1.0D + UnbreakingLevel));
                    }
                } else {
                    int j = 0;
                    for (int k = 0; UnbreakingLevel > 0 && k < pAmount; ++k) {
                        if (pRandom.nextInt(UnbreakingLevel + 1) > 0) {
                            ++j;
                        }
                    }
                    pAmount -= j;
                }
                if (pAmount <= 0) {
                    return false;
                }
            }

            if (pUser != null && pAmount != 0) {
                CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(pUser, (ItemStack)(Object) this, this.getDamageValue() + pAmount);
            }

            int l = this.getDamageValue() + pAmount;
            this.setDamageValue(l);
            return l >= this.getMaxDamage();
        }
    }

    /**
     * 耐久归零时不销毁物品，而是标记为 broken，并派发 {@link ItemBrokenEvent}（不可取消）。
     * <p>
     * 注入点选在原版 {@code shrink(1)} 指令处（该指令负责销毁物品）：
     * 在此之前 {@code consumer.accept(entity)} 已执行，故 ITEM_BREAK 音效照常播放；
     * cancel 后跳过 shrink 与后续的 {@code setDamageValue(0)}，物品保留并封顶耐久。
     * 配置关闭时走原版销毁逻辑（不派发事件）。
     *
     * @author THEREDK
     * @reason 损坏状态替代原版爆掉
     */
    @Inject(method = "hurtAndBreak", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void newArmorSystem$onBreak(int pAmount, LivingEntity pEntity, Consumer<LivingEntity> pConsumer, CallbackInfo ci) {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.COMMON.brokenStateEnabled.get()) {
            return;  // 配置关闭：保留原版销毁行为
        }
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            // 归一累积的 over-damage（damage > maxDamage → 回落封顶）。传值 == maxDamage，
            // 不满足 clearBrokenOnRepair 的 pDamageValue < maxDamage，不会误清标记。
            this.setDamageValue(this.getMaxDamage());
            ci.cancel();  // 已处于 broken：仅阻止再次销毁，不重复音效/粒子
            return;
        }
        this.setDamageValue(this.getMaxDamage());  // 封顶，保证损坏条归零、isBroken() 成立、不再继续损耗
        BrokenState.markBroken((ItemStack) (Object) this);
        // 派发物品损坏事件：仅状态转换这一次（已 broken 的重复损耗在上方早退，不再派发）；
        // 无监听者时行为与不派发完全一致。事件不可取消（不变式见 ItemBrokenEvent 类注释）。
        MinecraftForge.EVENT_BUS.post(new ItemBrokenEvent(pEntity, (ItemStack) (Object) this, pAmount));
        // 爆掉的粒子动画（物品碎裂粒子），仅服务端广播
        Level level = pEntity.level();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, (ItemStack) (Object) this),
                    pEntity.getX(), pEntity.getY() + pEntity.getEyeHeight() * 0.5D, pEntity.getZ(),
                    12, 0.25D, 0.25D, 0.25D, 0.1D);
        }
        ci.cancel();
    }

    /**
     * 耐久被修复到 maxDamage 以下时自动清除 broken 标记，恢复物品功能。
     * <p>
     * 铁砧材料修复、同类物品合并修复、经验修补（Mending）、命令/数据包修改等
     * 一切将耐久恢复到 maxDamage 以下的路径最终都会调用 {@code setDamageValue}，
     * 在此统一清除标记（对所有可修理物品生效，不限于护甲），保证不变式：
     * broken ⇔ 耐久 ≥ maxDamage。仅清除被修复物品自身的标记，不涉及其他物品。
     * <p>
     * 耐久增加方向（damage 上升、归零爆掉路径）不满足 {@code pDamageValue < maxDamage}，
     * 天然不会误清；broken 物品 damage 已封顶于 maxDamage，其耐久恢复必触发本方法。
     * <p>
     * 清除标记后派发 {@link ItemRepairedEvent}（不可取消），与 {@link ItemBrokenEvent}
     * （损坏方向）成对；无监听者时行为与不派发完全一致。
     *
     * @author THEREDK
     * @reason broken 物品修理后恢复功能
     */
    @Inject(method = "setDamageValue", at = @At("HEAD"))
    private void newArmorSystem$clearBrokenOnRepair(int pDamageValue, CallbackInfo ci) {
        // 判定顺序刻意如此：先做纯 NBT 的 broken 检查（getTag/isEmpty，不触碰物品注册表），
        // 再取 maxDamage。反过来写会在<b>每一次</b> setDamageValue 调用时都先执行 getMaxDamage() ——
        // 而 ItemStack 构造器本身就会调用 setDamageValue（ItemStack.java:152），
        // Bootstrap 阶段（Ingredient / PotionBrewing 引导构造物品堆）因此会命中它；
        // 其它模组注入 getMaxDamage() 时可能依赖尚未就绪的注册表，实测
        // morecraft 的 injectMaxDamage 在此时抛 NullPointerException:
        // Registry Object not present: morecraft:reinforcement，直接导致启动崩溃。
        // 非 broken 物品在第一个条件即短路，因此启动期不再触碰 getMaxDamage()。
        if (BrokenState.isBroken((ItemStack) (Object) this) && pDamageValue < this.getMaxDamage()) {
            BrokenState.clearBroken((ItemStack) (Object) this);
            // 派发物品修复事件：仅 broken → 正常的状态转换这一次（非 broken 物品的
            // 耐久变化不进入本分支）；事件不可取消（不变式见 ItemRepairedEvent 类注释）
            MinecraftForge.EVENT_BUS.post(new ItemRepairedEvent((ItemStack) (Object) this, pDamageValue));
        }
    }

    /**
     * 属性修饰符门控：
     * <ul>
     *   <li>broken 物品清空全部修饰符：护甲失去防御/韧性/击退抗性，武器失去攻击伤害加成。</li>
     *   <li>护甲（含其它模组护甲）的击退抗性加成全部无效化：原版下界合金护甲的
     *       {@code +0.1} 击退抗性及模组护甲的自定义击退抗性一律移除，
     *       由质量系统统一派生（{@link PlayerMassEffects}），保证"初始为 0、随负重增长"。</li>
     * </ul>
     *
     * <p><b>负数击退抗性接口</b>：此过滤仅作用于"护甲物品提供的修饰符"；
     * 附属模组若要实现"更易被击退"（负抗性 = 击退更远），请直接为玩家实体
     * {@code Attributes.KNOCKBACK_RESISTANCE} 添加负修饰符 —— 属性范围已放宽
     * （{@code Config.COMMON.knockbackResistanceNoLowerLimit} 默认开启 = 无下限；
     * 关闭后回退 {@code Config.COMMON.knockbackResistanceMinAllowed}，默认 -2.0），
     * 且 {@link PlayerMassEffects} 每 tick 只更新自身固定 UUID，不会移除外来修饰符。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果；击退抗性由质量系统统一供给
     */
    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$clearBrokenAttributes(EquipmentSlot pSlot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        Multimap<Attribute, AttributeModifier> map = cir.getReturnValue();
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(ImmutableMultimap.of());
            return;
        }
        if (this.getItem() instanceof ArmorItem && map.containsKey(Attributes.KNOCKBACK_RESISTANCE)) {
            LinkedHashMultimap<Attribute, AttributeModifier> filtered = LinkedHashMultimap.create();
            map.forEach((attribute, modifier) -> {
                if (attribute != Attributes.KNOCKBACK_RESISTANCE) {
                    filtered.put(attribute, modifier);
                }
            });
            cir.setReturnValue(filtered);
        }
    }

    /**
     * broken 工具按空手挖掘：getDestroySpeed 返回 1.0F（徒手速度）。
     * <p>
     * 与 {@link #newArmorSystem$blockBrokenHarvest} 的 {@code isCorrectToolForDrops=false} 配合：
     * 破坏速度 = 空手；{@code ForgeHooks.isCorrectToolForDrops} 走
     * {@code hasCorrectToolForDrops} → false → 速度除数 30→100，与空手完全一致。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$disableBrokenMining(BlockState pState, CallbackInfoReturnable<Float> cir) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(1.0F);
        }
    }

    /**
     * broken 工具视为挖掘等级不足（isCorrectToolForDrops = false），行为等价于空手：
     * <ul>
     *   <li>掉落门控：{@code ServerPlayerGameMode.destroyBlock} 与
     *       {@code BlockBehaviour.playerWillDestroy} 均经 {@code canHarvestBlock}
     *       → {@code Player.hasCorrectToolForDrops} → 主手 {@code isCorrectToolForDrops}；
     *       返回 false 使 {@code requiresCorrectToolForDrops} 方块（钻石矿等）不触发
     *       {@code playerDestroy}，掉落被抑制 —— 能挖但不掉。</li>
     *   <li>不要求正确工具的方块（泥土等）：{@code hasCorrectToolForDrops} 短路
     *       {@code !requiresCorrectToolForDrops()} 直接为 true，照常掉落。</li>
     *   <li>速度除数：{@code ForgeHooks.isCorrectToolForDrops} → false → ÷100（空手除数）。</li>
     * </ul>
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "isCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$blockBrokenHarvest(BlockState pState, CallbackInfoReturnable<Boolean> cir) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * broken 物品右键使用直接放行：食物/药水/鞘翅/烟花/弓/盾牌等一律不可用。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$blockBrokenUse(Level pLevel, Player pPlayer, InteractionHand pHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(InteractionResultHolder.pass((ItemStack) (Object) this));
        }
    }

    /**
     * 物品 tooltip 显示质量与质量系统派生效果。
     *
     * <p>门槛为<b>物品本身有质量</b>（{@code PlayerMass.getItemMass > 0}）：
     * 护甲（材料公式）与注册了自定义质量的非护甲物品（{@code PlayerMass.registerItemMass}，
     * 如饰品、重剑）均显示 {@code +X 质量}（蓝色，与原版属性前缀对齐）；
     * broken 物品与未注册质量的普通物品不显示任何质量系统行（broken 失去质量属性）。
     *
     * <p>质量行与 4 个 UI 行作为一个整体插入，位置按三级锚点依次尝试：
     * <ol>
     *   <li><b>attributeslib（Apothic Attributes）/ Apotheosis 属性区块</b>
     *       （{@link #newArmorSystem$findAttributesLibBlockEnd}，翻译键前缀
     *       {@code attributeslib.modifier.}）→ 插在该区块<b>最后一行</b>之后；
     *       该属性库会替换原版 {@code attribute.modifier.*} 行，故必须优先匹配，
     *       否则质量行会掉到列表末尾；</li>
     *   <li><b>原版属性行</b>：优先 "+X 盔甲韧性"
     *       （{@link #newArmorSystem$findArmorToughnessLine}，钻石/下界合金等有韧性护甲）
     *       → 插在韧性行下一行；否则 "+X 护甲值"
     *       （{@link #newArmorSystem$findArmorAttributeLine}，铁/金等无韧性护甲）
     *       → 插在护甲值行下一行；</li>
     *   <li>两者都找不到（含非护甲物品）→ 追加到列表末尾。</li>
     * </ol>
     *
     * <p>块内顺序与现状一致：{@code +X 质量} 行在前；持有者非空时显示
     * <b>当前负重比例</b>（灰色，无需 F3+H）；开启 F3+H（高级提示
     * {@link TooltipFlag#isAdvanced()}）再追加该物品<b>单独提供</b>的预览三项：
     * 负重比例（深灰，{@code +X%}）、移速减益（红色）、击退抗性（蓝色，
     * 以整数显示 {@code +30} 而非 {@code +30.0}）。预览仅取该物品自身质量
     * （物品质量 / 最大负重），不叠加当前穿戴总质量，也不与对应槽位已有装备比较。
     *
     * <p>注入 {@code getTooltipLines} 的 RETURN，与 {@link #newArmorSystem$addBrokenTooltip}
     * 同点，避免信息丢失。
     *
     * @author THEREDK
     * @reason 质量系统可视化，排版与原版护甲属性保持一致
     */
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void newArmorSystem$addMassTooltip(@Nullable Player pPlayer, TooltipFlag pIsAdvanced, CallbackInfoReturnable<List<Component>> cir) {
        List<Component> lines = cir.getReturnValue();
        int mass = PlayerMass.getItemMass((ItemStack) (Object) this);
        if (mass <= 0) {
            return;
        }
        List<Component> extra = new ArrayList<>(5);
        extra.add(Component.translatable("tooltip.new_armor_system.mass", mass).withStyle(ChatFormatting.BLUE));
        if (pPlayer != null) {
            // 当前负重比例：灰色，始终显示（无需 F3+H）
            double ratio = PlayerMass.getLoadRatio(pPlayer);
            String percent = String.format(Locale.ROOT, "%.1f%%", ratio * 100);
            extra.add(Component.translatable("tooltip.new_armor_system.load_ratio", percent).withStyle(ChatFormatting.GRAY));
            if (pIsAdvanced.isAdvanced()) {
                // 负重比例（预览）：深灰；该物品单独提供的负重比例（物品质量 / 最大负重，不含当前穿戴）
                String previewPercent = String.format(Locale.ROOT, "+%.1f%%", PlayerMass.getPreviewLoadRatio(pPlayer, mass) * 100);
                extra.add(Component.translatable("tooltip.new_armor_system.load_ratio_preview", previewPercent).withStyle(ChatFormatting.DARK_GRAY));
                // 移速减益（预览）：红色
                String speedPenalty = String.format(Locale.ROOT, "-%.1f%%", PlayerMass.getPreviewSpeedPenalty(pPlayer, mass) * 100);
                extra.add(Component.translatable("tooltip.new_armor_system.speed_penalty", speedPenalty).withStyle(ChatFormatting.RED));
                // 击退抗性（预览）：蓝色
                String knockback = String.format(Locale.ROOT, "+%d", (int) PlayerMass.getPreviewKnockbackResistancePoints(pPlayer, mass));
                extra.add(Component.translatable("tooltip.new_armor_system.knockback_resistance", knockback).withStyle(ChatFormatting.BLUE));
            }
        }
        // 锚点 1：attributeslib / Apotheosis 属性区块 —— 插在该区块最后一行之后
        int attrLibEnd = newArmorSystem$findAttributesLibBlockEnd(lines);
        if (attrLibEnd >= 0) {
            lines.addAll(attrLibEnd + 1, extra);
            return;
        }
        // 锚点 2：原版属性行 —— 有韧性护甲插在韧性行下一行；无韧性（含非护甲）插在护甲值行下一行
        int vanillaAttrLine = newArmorSystem$findArmorToughnessLine(lines);
        if (vanillaAttrLine < 0) {
            vanillaAttrLine = newArmorSystem$findArmorAttributeLine(lines);
        }
        if (vanillaAttrLine >= 0) {
            lines.addAll(vanillaAttrLine + 1, extra);
            return;
        }
        // 锚点 3：找不到任何属性行（如自定义护甲无护甲属性）→ 追加到列表末尾
        lines.addAll(extra);
    }

    /**
     * 在 tooltip 行列表中定位 "+X 护甲值"（{@code attribute.modifier.plus.0/1/2} + {@code attribute.name.generic.armor}）
     * 的索引，用于把质量系统行插入到原版护甲属性下方。找不到时返回 -1。
     */
    @Unique
    private static int newArmorSystem$findArmorAttributeLine(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getContents() instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if ((key.startsWith("attribute.modifier.plus.") || key.startsWith("attribute.modifier.take."))
                        && newArmorSystem$hasArgTranslatable(tc, "attribute.name.generic.armor")) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 在 tooltip 行列表中定位 "+X 盔甲韧性"（{@code attribute.modifier.plus.0/1/2} +
     * {@code attribute.name.generic.armor_toughness}）的索引，用于把质量系统
     * （质量行 + 4 个 UI 行）插入到原版韧性属性下方（针对有韧性的护甲）。
     * 找不到时返回 -1。
     */
    @Unique
    private static int newArmorSystem$findArmorToughnessLine(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getContents() instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if ((key.startsWith("attribute.modifier.plus.") || key.startsWith("attribute.modifier.take."))
                        && newArmorSystem$hasArgTranslatable(tc, "attribute.name.generic.armor_toughness")) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 在 tooltip 行列表中定位 <b>attributeslib（Apothic Attributes）/ Apotheosis 属性区块</b>
     * 的<b>最后一行</b>索引，用于把质量系统行整体插在该区块下方。找不到时返回 -1。
     *
     * <p>attributeslib 的 {@code ItemStackMixin} 会拦截 {@code ItemStack#getTooltipLines}
     * 中 {@code shouldShowInTooltip(..., TooltipPart.MODIFIERS)} 的判定并自行渲染属性行，
     * 其翻译键为 {@code attributeslib.modifier.plus} / {@code .take} / {@code .bool}
     * （见 attributeslib 语言文件 {@code assets/attributeslib/lang/*.json}），
     * 与<b>原版</b> {@code attribute.modifier.plus.N} 完全不同。
     * 因此仅按原版键匹配会漏判，导致质量行掉到列表末尾（原版键匹配见
     * {@link #newArmorSystem$findArmorAttributeLine}）。
     *
     * <p>取<b>最后一行</b>而非第一行：属性区块通常含多行（护甲值、盔甲韧性、
     * attributeslib 自定义属性如 {@code attributeslib:armor_pierce} 等），
     * 插在末行之后可保证质量行位于整个属性区块下方，排版稳定。
     */
    @Unique
    private static int newArmorSystem$findAttributesLibBlockEnd(List<Component> lines) {
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getContents() instanceof TranslatableContents tc
                    && tc.getKey().startsWith("attributeslib.modifier.")) {
                last = i;
            }
        }
        return last;
    }

    /**
     * 检查给定可翻译组件的参数列表中是否包含指定翻译 key 的子组件。
     */
    @Unique
    private static boolean newArmorSystem$hasArgTranslatable(TranslatableContents tc, String targetKey) {
        for (Object arg : tc.getArgs()) {
            if (arg instanceof Component argComp
                    && argComp.getContents() instanceof TranslatableContents attrTc
                    && attrTc.getKey().equals(targetKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在 tooltip 行列表中定位注册名行（如 {@code minecraft:iron_chestplate}）。
     *
     * <p>原版高级提示（F3+H）下物品无自定义名称时，第 2 行为
     * {@code Component.literal(item.getDescriptionId())}（1.19+ 为注册名，
     * 含命名空间 {@code namespace:path}）且样式为深灰（{@link ChatFormatting#DARK_GRAY}）。
     * 据此特征识别：纯文本、含 {@code :}、深灰。找不到时返回 -1。
     */
    @Unique
    private static int newArmorSystem$findRegistryNameLine(List<Component> lines) {
        Integer darkGrayRgb = ChatFormatting.DARK_GRAY.getColor();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getContents() instanceof LiteralContents literal) {
                String text = literal.text();
                if (darkGrayRgb != null && text.indexOf(':') > 0 && line.getStyle().getColor() != null
                        && line.getStyle().getColor().getValue() == darkGrayRgb) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * broken 物品显示深红色斜体"损坏的 / Broken"标识。
     * <p>
     * 深红色采用 {@link ChatFormatting#DARK_RED}（§4，铁砧"过于昂贵"提示同款深红）。
     * 高级提示（F3+H）开启时插到注册名行（如 {@code minecraft:iron_chestplate}）上方、
     * 紧跟物品名；未开启高级提示（无注册名行）时保持追加到列表末尾。
     * 注入 {@code getTooltipLines} 的 RETURN：此时附魔名（{@code getEnchantmentTags} 直读 NBT）
     * 与 Forge {@code onItemTooltip} 事件均已处理完毕，不影响附魔名显示与物品附魔光效。
     *
     * @author THEREDK
     * @reason broken 视觉标识
     */
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void newArmorSystem$addBrokenTooltip(@Nullable Player pPlayer, TooltipFlag pIsAdvanced, CallbackInfoReturnable<List<Component>> cir) {
        if (!BrokenState.isBroken((ItemStack) (Object) this)) {
            return;
        }
        List<Component> lines = cir.getReturnValue();
        Component brokenLine = Component.translatable("tooltip.new_armor_system.broken")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        if (pIsAdvanced.isAdvanced()) {
            int registryLine = newArmorSystem$findRegistryNameLine(lines);
            if (registryLine >= 0) {
                lines.add(registryLine, brokenLine);
                return;
            }
        }
        lines.add(brokenLine);
    }
}
