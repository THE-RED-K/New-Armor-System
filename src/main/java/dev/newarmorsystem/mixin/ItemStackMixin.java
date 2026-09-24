package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.BrokenState;
import dev.newarmorsystem.api.Config;
import dev.newarmorsystem.api.ItemBrokenEvent;
import dev.newarmorsystem.api.ItemRepairedEvent;
import dev.newarmorsystem.api.PlayerMass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 损坏状态（broken）接管 —— 耐久归零的物品不再销毁，而是转入 broken 状态：
 * 保留物品、失去全部效果（属性 / 挖掘 / 使用 / 附魔），仍可被修复。
 *
 * <p>另承担两处与质量系统相关的物品侧逻辑：护甲自身击退抗性无效化、质量 tooltip 展示。
 *
 * <p><b>1.20.1 → 1.21.1 的位置迁移</b>：1.20.1 的耐久附魔减免写在
 * {@code ItemStack#hurt(int, RandomSource, ServerPlayer)} 里、损坏接管写在
 * {@code hurtAndBreak(int, LivingEntity, Consumer)} 的 {@code shrink} 处；
 * 1.21.1 取消了 {@code hurt}，耐久附魔减免被 NeoForge 收进
 * {@code EnchantmentHelper#processDurabilityChange}（由「耐久附魔修改」模块另行接管），
 * 此处只接管损坏判定与 broken 语义。
 *
 * <p>不要在此处添加 {@code remap = false}。
 *
 * @author THEREDK
 * @reason 损坏状态替代原版爆掉
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /**
     * 耐久归零时不销毁物品，而是标记为 broken，并派发 {@link ItemBrokenEvent}（不可取消）。
     *
     * <p>注入点选在原版 {@code shrink(1)} 指令处（该指令负责销毁物品）。1.21.1 的顺序是
     * {@code shrink(1)} → {@code onBreak.accept(item)}（销毁回调：音效 / 统计 /
     * {@code onEquippedItemBroken}），与原版 1.20.1 相反；因此本方法在 {@code shrink} 前
     * 取消并<b>自行调用 {@code onBreak}</b>，保证销毁反馈（音效等）照常触发，
     * 而物品保留、耐久封顶。
     *
     * @author THEREDK
     * @reason 损坏状态替代原版爆掉
     */
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"),
            cancellable = true)
    private void newArmorSystem$onBreak(int amount, ServerLevel level, @Nullable LivingEntity entity,
                                        Consumer<Item> onBreak, CallbackInfo ci) {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.COMMON.brokenStateEnabled.get()) {
            return;  // 配置关闭：保留原版销毁行为
        }
        ItemStack self = (ItemStack) (Object) this;
        if (BrokenState.isBroken(self)) {
            // 归一累积的 over-damage（damage > maxDamage → 回落封顶）。传值 == maxDamage，
            // 不满足 clearBrokenOnRepair 的 newDamage < maxDamage，不会误清标记。
            self.setDamageValue(self.getMaxDamage());
            ci.cancel();  // 已处于 broken：仅阻止再次销毁，不重复音效/粒子
            return;
        }
        self.setDamageValue(self.getMaxDamage());  // 封顶，保证损坏条归零、isBroken() 成立、不再继续损耗
        BrokenState.markBroken(self);
        // 派发物品损坏事件：仅状态转换这一次（已 broken 的重复损耗在上方早退，不再派发）；
        // 无监听者时行为与不派发完全一致。事件不可取消。
        // 无实体上下文时（entity == null）无法构造 LivingEvent，仅静默转入 broken。
        if (entity != null) {
            NeoForge.EVENT_BUS.post(new ItemBrokenEvent(entity, self, amount));
            // 爆掉的粒子动画（物品碎裂粒子），仅服务端广播
            if (level != null) {
                level.sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, self),
                        entity.getX(), entity.getY() + entity.getEyeHeight() * 0.5D, entity.getZ(),
                        12, 0.25D, 0.25D, 0.25D, 0.1D);
            }
        }
        // 保留原版销毁回调（音效 / 统计 / onEquippedItemBroken）；调用点与语义同原版
        onBreak.accept(self.getItem());
        ci.cancel();
    }

    /**
     * 耐久被修复到 maxDamage 以下时自动清除 broken 标记，恢复物品功能。
     *
     * <p>铁砧材料修复、同类物品合并修复、经验修补（Mending）、命令/数据包修改等
     * 一切将耐久恢复到 maxDamage 以下的路径最终都会调用 {@code setDamageValue}，
     * 在此统一清除标记（对所有可修理物品生效，不限于护甲），保证不变式：
     * broken ⇔ 耐久 ≥ maxDamage。仅清除被修复物品自身的标记，不涉及其他物品。
     *
     * <p>判定顺序刻意如此：先做纯数据组件的 broken 检查（{@code isEmpty} + 读组件，
     * 不触碰物品注册表），再取 maxDamage。{@code ItemStack} 构造器本身会经由
     * {@code Item#verifyComponentsAfterLoad} 调用 {@code setDamageValue}，
     * 非 broken 物品在第一个条件即短路，因此启动期不再触碰 {@code getMaxDamage()}。
     *
     * <p>清理标记后派发 {@link ItemRepairedEvent}（不可取消），与 {@link ItemBrokenEvent}
     * （损坏方向）成对；无监听者时行为与不派发完全一致。
     *
     * @author THEREDK
     * @reason broken 物品修理后恢复功能
     */
    @Inject(method = "setDamageValue", at = @At("HEAD"))
    private void newArmorSystem$clearBrokenOnRepair(int newDamage, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (BrokenState.isBroken(self) && newDamage < self.getMaxDamage()) {
            BrokenState.clearBroken(self);
            NeoForge.EVENT_BUS.post(new ItemRepairedEvent(self, newDamage));
        }
    }

    /**
     * 属性修饰符门控：broken 物品清空全部修饰符（护甲失去防御/韧性/击退抗性，
     * 武器失去攻击伤害加成）。
     *
     * <p>1.20.1 注入的是 {@code getAttributeModifiers(EquipmentSlot)}；1.21.1 装备属性的
     * 实际应用路径是 {@code forEachModifier}，故两个重载（槽位组 / 单槽位）都接管。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$clearBrokenModifiersGroup(EquipmentSlotGroup slotGroup,
                                                          BiConsumer<Holder<Attribute>, AttributeModifier> action,
                                                          CallbackInfo ci) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            ci.cancel();
        }
    }

    /** {@link #newArmorSystem$clearBrokenModifiersGroup} 的单槽位重载版本。 */
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void newArmorSystem$clearBrokenModifiersSlot(EquipmentSlot slot,
                                                         BiConsumer<Holder<Attribute>, AttributeModifier> action,
                                                         CallbackInfo ci) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * 护甲提供的<b>击退抗性</b>修饰符全部无效化：原版下界合金护甲的 {@code +0.1}
     * 击退抗性及模组护甲的自定义击退抗性一律移除，由质量系统统一派生
     * （{@code PlayerMassEffects}），保证"初始为 0、随负重增长"。
     *
     * <p><b>负数击退抗性接口</b>：此过滤仅作用于"护甲物品提供的修饰符"；
     * 附属模组若要实现"更易被击退"（负抗性 = 击退更远），请直接为玩家实体
     * {@code Attributes.KNOCKBACK_RESISTANCE} 添加负修饰符 —— 属性范围已放宽，
     * 且 {@code PlayerMassEffects} 每 tick 只更新自身固定 ID 的修饰符，不会移除外来修饰符。
     *
     * <p>1.20.1 注入的是 {@code getAttributeModifiers(EquipmentSlot)} 的返回值过滤；
     * 1.21.1 装备属性的实际应用路径是 {@code forEachModifier}，故改为包裹其内部的
     * 物品属性迭代（{@link ItemAttributeModifiers#forEach}）—— 只过滤物品提供的条目，
     * 不影响同方法内附魔提供的属性修饰符。
     *
     * @author THEREDK
     * @reason 击退抗性由质量系统统一供给
     */
    @Redirect(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"))
    private void newArmorSystem$stripArmorKnockbackResistance(ItemAttributeModifiers instance, EquipmentSlot slot,
                                                              BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        if (((ItemStack) (Object) this).getItem() instanceof ArmorItem) {
            instance.forEach(slot, (attribute, modifier) -> {
                if (attribute.value() != Attributes.KNOCKBACK_RESISTANCE.value()) {
                    action.accept(attribute, modifier);
                }
            });
        } else {
            instance.forEach(slot, action);
        }
    }

    /**
     * broken 工具按空手挖掘：getDestroySpeed 返回 1.0F（徒手速度）。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$disableBrokenMining(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (BrokenState.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(1.0F);
        }
    }

    /**
     * broken 工具视为挖掘等级不足（isCorrectToolForDrops = false），行为等价于空手。
     *
     * @author THEREDK
     * @reason broken 物品失去所有效果
     */
    @Inject(method = "isCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$blockBrokenHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
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
    private void newArmorSystem$blockBrokenUse(Level level, Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (BrokenState.isBroken(self)) {
            cir.setReturnValue(InteractionResultHolder.pass(self));
        }
    }

    /**
     * broken 物品显示深红色斜体"损坏的 / Broken"标识。
     * <p>
     * 深红色采用 {@link ChatFormatting#DARK_RED}（§4，铁砧"过于昂贵"提示同款深红）。
     * 高级提示（F3+H）开启时插到注册名行（如 {@code minecraft:iron_chestplate}）上方、
     * 紧跟物品名；未开启高级提示（无注册名行）时保持追加到列表末尾。
     *
     * @author THEREDK
     * @reason broken 视觉标识
     */
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void newArmorSystem$addBrokenTooltip(Item.TooltipContext context, @Nullable Player player,
                                                 TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (!BrokenState.isBroken((ItemStack) (Object) this)) {
            return;
        }
        List<Component> lines = cir.getReturnValue();
        Component brokenLine = Component.translatable("tooltip.new_armor_system.broken")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        if (flag.isAdvanced()) {
            int registryLine = newArmorSystem$findRegistryNameLine(lines);
            if (registryLine >= 0) {
                lines.add(registryLine, brokenLine);
                return;
            }
        }
        lines.add(brokenLine);
    }

    /**
     * 物品 tooltip 显示质量与质量系统派生效果。
     *
     * <p>门槛为<b>物品本身有质量</b>（{@code PlayerMass.getItemMass > 0}）：
     * 护甲（材料公式）与注册了自定义质量的非护甲物品（{@code PlayerMass.registerItemMass}）
     * 均显示 {@code +X 质量}；broken 物品与未注册质量的普通物品不显示。
     *
     * <p>块内顺序：{@code +X 质量} 行在前；持有者非空时显示<b>当前负重比例</b>（灰色，
     * 无需 F3+H）；开启 F3+H（高级提示）再追加该物品<b>单独提供</b>的预览三项：
     * 负重比例（深灰）、移速减益（红色）、击退抗性（蓝色，整数显示）。
     *
     * <p>插入位置按三级锚点依次尝试：attributeslib/Apotheosis 属性区块 →
     * 原版属性行（韧性优先，其次护甲值）→ 追加到列表末尾。
     *
     * @author THEREDK
     * @reason 质量系统可视化，排版与原版护甲属性保持一致
     */
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void newArmorSystem$addMassTooltip(Item.TooltipContext context, @Nullable Player player,
                                               TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        List<Component> lines = cir.getReturnValue();
        int mass = PlayerMass.getItemMass((ItemStack) (Object) this);
        if (mass <= 0) {
            return;
        }
        List<Component> extra = new ArrayList<>(5);
        extra.add(Component.translatable("tooltip.new_armor_system.mass", mass).withStyle(ChatFormatting.BLUE));
        if (player != null) {
            // 当前负重比例：灰色，始终显示（无需 F3+H）
            double ratio = PlayerMass.getLoadRatio(player);
            String percent = String.format(Locale.ROOT, "%.1f%%", ratio * 100);
            extra.add(Component.translatable("tooltip.new_armor_system.load_ratio", percent)
                    .withStyle(ChatFormatting.GRAY));
            if (flag.isAdvanced()) {
                // 负重比例（预览）：深灰；该物品单独提供的负重比例（物品质量 / 最大负重，不含当前穿戴）
                String previewPercent = String.format(Locale.ROOT, "+%.1f%%",
                        PlayerMass.getPreviewLoadRatio(player, mass) * 100);
                extra.add(Component.translatable("tooltip.new_armor_system.load_ratio_preview", previewPercent)
                        .withStyle(ChatFormatting.DARK_GRAY));
                // 移速减益（预览）：红色
                String speedPenalty = String.format(Locale.ROOT, "-%.1f%%",
                        PlayerMass.getPreviewSpeedPenalty(player, mass) * 100);
                extra.add(Component.translatable("tooltip.new_armor_system.speed_penalty", speedPenalty)
                        .withStyle(ChatFormatting.RED));
                // 击退抗性（预览）：蓝色
                String knockback = String.format(Locale.ROOT, "+%d",
                        (int) PlayerMass.getPreviewKnockbackResistancePoints(player, mass));
                extra.add(Component.translatable("tooltip.new_armor_system.knockback_resistance", knockback)
                        .withStyle(ChatFormatting.BLUE));
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
     * 在 tooltip 行列表中定位 "+X 护甲值"（{@code attribute.modifier.plus.0/1/2}
     * + {@code attribute.name.generic.armor}）的索引。找不到时返回 -1。
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
     * 在 tooltip 行列表中定位 "+X 盔甲韧性"
     * （{@code attribute.name.generic.armor_toughness}）的索引。找不到时返回 -1。
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
     * 的<b>最后一行</b>索引（翻译键前缀 {@code attributeslib.modifier.}）。找不到时返回 -1。
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

    /** 检查给定可翻译组件的参数列表中是否包含指定翻译 key 的子组件。 */
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
            if (line.getContents() instanceof PlainTextContents.LiteralContents literal) {
                String text = literal.text();
                if (darkGrayRgb != null && text.indexOf(':') > 0 && line.getStyle().getColor() != null
                        && line.getStyle().getColor().getValue() == darkGrayRgb) {
                    return i;
                }
            }
        }
        return -1;
    }
}
