package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorAttributeRules;
import dev.newarmorsystem.api.Config;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * 兼容模块的<b>护甲值 / 盔甲韧性</b>登记入口 —— 受「实验性功能总开关」门控。
 *
 * <p><b>为什么单独抽一层</b>：本模组对其它模组护甲做的适配分两类，语义不同：
 * <ul>
 *   <li><b>机制适配</b>（护甲类型、耐久基数、质量系数、修理系数、工具绑定）——
 *       让该模组的护甲能享受本系统的机制，属于本模组<b>自带内容</b>，始终生效；</li>
 *   <li><b>数值改写</b>（护甲值 / 盔甲韧性）——<b>属于整合包内容</b>：
 *       具体数值是某个整合包为拉开梯度而选的，不该由模组替所有玩家决定，
 *       故统一走本入口，由 {@code feature_toggles.experimentalFeaturesEnabled}
 *       这一个<b>实验性功能总开关</b>控制，默认<b>关闭</b>（保持各模组原版数值）。</li>
 * </ul>
 *
 * <p>各兼容模块因此只需把原先直接调用 {@link ArmorAttributeRules#register} 的地方
 * 改为调用本类，数值表与登记粒度（材料级 / 逐件级）保持不变。
 *
 * <p><b>关闭时的行为</b>：直接返回，不写入任何登记，各护甲数值完全保持各自模组的原版
 * （本模组对护甲值的其它机制不受影响）。
 *
 * @author THEREDK
 */
public final class CompatArmorAttributes {

    private CompatArmorAttributes() {
    }

    /**
     * 登记一件（材料 × 部位）的护甲值与盔甲韧性。
     *
     * <p>总开关关闭（默认）或配置未加载时直接返回，不产生任何登记。
     *
     * @param material  护甲材料实例（材料级登记：该材料下全部该部位护甲一并生效）
     * @param type      部位（头盔 / 胸甲 / 护腿 / 靴子）
     * @param armor     护甲值（≥ 0；0 = 该部位移除护甲值）
     * @param toughness 盔甲韧性（≥ 0；0 = 该部位移除盔甲韧性）
     */
    public static void register(ArmorMaterial material, ArmorItem.Type type, double armor, double toughness) {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.COMMON.experimentalFeaturesEnabled.get()) {
            return;
        }
        ArmorAttributeRules.register(material, type, armor, toughness);
    }
}
