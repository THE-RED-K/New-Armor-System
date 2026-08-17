package dev.newarmorsystem.mixin;

import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.ArmorMaterialRules;
import dev.newarmorsystem.api.Config;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;

/**
 * 重写护甲耐久体系的基础数据（部位系数与材料耐久基数）。
 *
 * <p>部位系数：原版 {13,15,16,11}(boots/legs/chest/head) 与工作台配方无关联，
 * 这是原版的逻辑断裂。新系统下：部位系数 = 基底(默认8) + 制作所需材料个数，
 * 即 head=13, chest=16, legs=15, boots=12（对应 8+5 / 8+8 / 8+7 / 8+4）。
 *
 * <p>材料耐久基数：对应现实莫氏硬度比例（皮革8/金10/铁16/钻石40），
 * 全部可配置；锁链套读铁套数值；下界合金失去自带基数，
 * 运行时按 钻石 + 金/2 联动（基于合成表）。
 *
 * <p>护甲类型系数（AT）：轻甲 0.75 / 中甲 1.0 / 重甲 1.5，全部可配置。
 * 分类：皮革、锁链为轻甲（锁链因此耐久低于铁套，为未来新护甲预留区分度）；
 * 海龟为重甲；金/铁/钻石/下界合金为中甲。
 * 最终耐久 = 部位系数 × 材料基数 × AT。
 *
 * @author THEREDK
 * @reason 新护甲系统的耐久体系与配方/硬度对齐，属于设计定位，与原版数值冲突为预期。
 */
@Mixin(ArmorMaterials.class)
public abstract class ArmorMaterialsMixin {

    @Shadow
    @Final
    private static EnumMap<ArmorItem.Type, Integer> HEALTH_FUNCTION_FOR_TYPE;

    /**
     * 重算部位系数。基底可配置。
     * JVM 保证本类 &lt;clinit&gt; 先于 Items 注册执行，注册快照读到的必为新值。
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void newArmorSystem$applySlotFactors(CallbackInfo ci) {
        int base = Config.COMMON_SPEC.isLoaded() ? Config.COMMON.slotFactorBase.get() : 8;
        HEALTH_FUNCTION_FOR_TYPE.put(ArmorItem.Type.HELMET, base + 5);
        HEALTH_FUNCTION_FOR_TYPE.put(ArmorItem.Type.CHESTPLATE, base + 8);
        HEALTH_FUNCTION_FOR_TYPE.put(ArmorItem.Type.LEGGINGS, base + 7);
        HEALTH_FUNCTION_FOR_TYPE.put(ArmorItem.Type.BOOTS, base + 4);
    }

    /**
     * 重算材料耐久基数并乘护甲类型系数（AT）。
     * 配方 = 部位系数 × 基数 × AT；读取时 HEALTH_FUNCTION_FOR_TYPE 已是新值，三者天然叠加。
     * 分类与基数分别走 {@link ArmorClass} / {@link ArmorMaterialRules} 统一查询。
     *
     * <p>枚举扩展材料（其他模组用 EnumHelper 向本枚举注入的新值）无内置 switch 匹配，
     * 其当前返回值仍是原版 部位系数×基数：据此反推基数并经
     * {@link ArmorMaterialRules#cacheExtendedDurabilityBase} 缓存（供铁砧修复端查询），
     * 使耐久与修复量都纳入新公式，兼容模组。
     */
    @Inject(method = "getDurabilityForType", at = @At("RETURN"), cancellable = true)
    private void newArmorSystem$applyDurabilityBase(ArmorItem.Type type, CallbackInfoReturnable<Integer> cir) {
        ArmorMaterial material = (ArmorMaterial) (Object) this;
        ArmorClass clazz = ArmorClass.of(material);
        double base = ArmorMaterialRules.durabilityBase(material);
        if (base < 0) {
            // 枚举扩展材料：当前返回值 = 原版部位系数 × 基数，反推并缓存
            int vanilla = cir.getReturnValueI();
            int slotFactor = ArmorMaterialRules.vanillaSlotFactor(type);
            if (vanilla > 0 && slotFactor > 0) {
                base = vanilla / (double) slotFactor;
                ArmorMaterialRules.cacheExtendedDurabilityBase(material, base);
            }
        }
        if (base <= 0) {
            return; // 兜底：保持原版
        }
        cir.setReturnValue((int) (HEALTH_FUNCTION_FOR_TYPE.get(type) * base * clazz.ArmorTypeCoefficient()));
    }
}
