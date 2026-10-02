package dev.newarmorsystem.api;

import dev.newarmorsystem.NewArmorSystem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组注册的<b>自定义属性</b>。
 *
 * <p><b>为什么需要它</b>：本模组的摔落公式原本只能被"全局分母（{@code fallDamageDenominator}）"和
 * 原版 {@code LivingFallEvent} 的 per-event 倍率调整 —— 前者对所有人都一样，后者只在<b>单次摔落</b>时
 * 有效。二者都无法表达"<b>某个玩家</b>的摔落伤害倍率"（例如某双靴子减免摔落伤害、某种诅咒翻倍），
 * 而 NeoForge 1.21.1 恰好用同名实体属性 {@code fall_damage_multiplier} 表达这件事。
 * 本类把该能力补到 1.20.1，并让 {@link FallDamage} 的公式读取它。
 *
 * <p><b>属性语义</b>：最终摔落伤害<b>乘</b>该属性值 —— {@code 0} = 完全免疫摔落伤害，
 * {@code 1} = 原样（默认），{@code 2} = 双倍；范围 {@code 0 ~ 1024}。因此它既接受装备/药水/
 * Curios 等标准的属性修饰符（{@code ADDITION} / {@code MULTIPLY_BASE} / {@code MULTIPLY_TOTAL}），
 * 也可被附属模组直接读取。0 值同时会让原版的"是否造成伤害"整数闸门归零，即<b>不播摔落音效、
 * 不进入 {@code hurt} 分支</b> —— 与原版倍率 0 的行为一致。
 *
 * <p><b>挂载范围</b>：目前只挂到<b>玩家</b>（{@link EntityType#PLAYER}），因为本模组的摔落公式
 * 只接管玩家；其余实体保持原版摔落伤害，不需要该属性。若将来要覆盖生物，在
 * {@link #onEntityAttributeModification} 里补一行即可。
 *
 * <p><b>命名空间</b>：注册名为 {@code new_armor_system:fall_damage_multiplier}；描述键
 * {@code attribute.new_armor_system.fall_damage_multiplier}（语言文件 中/英 均已提供）。
 *
 * @author THEREDK
 */
public final class ModAttributes {

    /** 属性注册表；由主类在构造期挂到 mod 事件总线（{@code ModAttributes.ATTRIBUTES.register(modEventBus)}）。 */
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, NewArmorSystem.MOD_ID);

    /**
     * 摔落伤害倍率（默认 {@code 1.0}，范围 {@code 0 ~ 1024}）：最终摔落伤害乘以该值。
     *
     * <p>与 1.21 的原版 {@code Attributes.FALL_DAMAGE_MULTIPLIER} <b>同语义</b>，便于整合包在两版
     * 之间复用同一套数值与文档。
     *
     * <p><b>与移植库（如 {@code portlib} / MesdagPortLib）可共存</b> —— 三条纪律：
     * <ol>
     *   <li>移植库<b>自己的 mixin 必须禁用</b>（黑名单）：它给 {@code hurt} 的伤害参数做乘法，
     *       与本模组在公式里给出的绝对值不兼容（旧版更直接互斥、启动崩溃，见
     *       {@code LivingEntityFallDamageMixin} 的说明）；</li>
     *   <li>但禁用 mixin <b>不影响属性注册</b>：注册发生在移植库主类（静态字段 + Supplier），
     *       与 mixin 无关 ⇒ 属性照旧存在、其它模组（饰品等）写入的修饰符照旧保留，
     *       缺的只是"读取并应用"这一步；</li>
     *   <li>于是<b>应用者唯一 = 本模组</b>：公式把两份倍率相乘（本模组属性 × 移植库属性，
     *       各默认 1.0、均 0 = 免疫），既不会"乘两次"也不会"被覆盖"；
     *       安全高度的移植属性同理，在 {@code fallSafeHeight = -1}（跟随平台）时被读取 ——
     *       读法见 {@link dev.newarmorsystem.compat.PortLibCompat}（按注册表路径查，不需要
     *       编译期依赖移植库）。</li>
     * </ol>
     */
    public static final RegistryObject<Attribute> FALL_DAMAGE_MULTIPLIER = ATTRIBUTES.register(
            "fall_damage_multiplier",
            () -> new RangedAttribute("attribute.new_armor_system.fall_damage_multiplier",
                    1.0D, 0.0D, 1024.0D).setSyncable(true));

    private ModAttributes() {
    }

    /**
     * 把自定义属性挂到玩家实体类型上（由主类注册到 mod 事件总线）。
     *
     * <p>挂在 {@link EntityAttributeModificationEvent} 而不是改实体的属性构建器：这是 Forge
     * 为"给既有实体类型追加属性"提供的标准入口，不需要 Mixin，也不会与其它模组的追加冲突。
     *
     * @param event 实体属性追加事件
     */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, FALL_DAMAGE_MULTIPLIER.get());
    }

    /**
     * 读取玩家的摔落伤害倍率。
     *
     * <p>属性实例缺失（属性未注册/实体非玩家/被其它模组摘掉）时返回 {@code 1.0} ——
     * 即"不改变摔落伤害"，保证本类在任何异常环境下的行为都与未启用时一致。
     *
     * @param player 目标玩家
     * @return 倍率（正常为 0 ~ 1024）
     */
    public static double fallDamageMultiplier(Player player) {
        AttributeInstance instance = player.getAttribute(FALL_DAMAGE_MULTIPLIER.get());
        return instance == null ? 1.0D : instance.getValue();
    }
}
