package dev.newarmorsystem.api;


import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common{
        // ===== 功能总开关 =====
        // 除「护甲减伤公式」与「护甲耐久损耗公式」是不可关闭的核心功能外，
        // 其余功能均可单独关闭；关闭后该功能完全回到原版行为。
        public final ForgeConfigSpec.BooleanValue armorAttributeOverrideEnabled;
        public final ForgeConfigSpec.BooleanValue armorDurabilitySystemEnabled;
        public final ForgeConfigSpec.BooleanValue unbreakingRedefinitionEnabled;
        public final ForgeConfigSpec.BooleanValue repairAmountFormulaEnabled;
        public final ForgeConfigSpec.BooleanValue massSystemEnabled;
        public final ForgeConfigSpec.BooleanValue speedPenaltyEnabled;
        // 超重惩罚开关（默认开启）：超重时移速/攻速/游泳推进全部归零（×0，而非减法），其它模组的加成无法抵消
        public final ForgeConfigSpec.BooleanValue overloadZeroEnabled;
        public final ForgeConfigSpec.BooleanValue knockbackResistanceEnabled;
        public final ForgeConfigSpec.BooleanValue massTooltipEnabled;
        public final ForgeConfigSpec.BooleanValue damageReflectionEnabled;
        public final ForgeConfigSpec.BooleanValue fallDamageFormulaEnabled;
        // 实验性内容总开关（默认关闭，与其它开关相反）：门控"整合包决策类"的数值改写，见 compat.CompatArmorAttributes
        public final ForgeConfigSpec.BooleanValue experimentalFeaturesEnabled;

        public final ForgeConfigSpec.DoubleValue lowZoneCoefficient;
        public final ForgeConfigSpec.DoubleValue highZoneCoefficient;
        public final ForgeConfigSpec.DoubleValue highZoneConstant;
        public final ForgeConfigSpec.IntValue armorToughnessMinAllowed;
        public final ForgeConfigSpec.DoubleValue maxReduction;
        public final ForgeConfigSpec.DoubleValue minReduction;
        public final ForgeConfigSpec.DoubleValue durabilityReductionConstant;
        public final ForgeConfigSpec.DoubleValue toughnessCoefficient;
        public final ForgeConfigSpec.DoubleValue unbreakingCoefficient;
        // 荆棘每级反伤系数（damage_reflection.thornsCoefficient，默认 0.15，不封顶）
        public final ForgeConfigSpec.DoubleValue thornsCoefficient;

        // 部位系数修正（部位基数 + 制作所需材料个数）
        public final ForgeConfigSpec.IntValue slotFactorBase;

        // 耐久基数（对应现实中的莫氏硬度）
        public final ForgeConfigSpec.IntValue leatherDurabilityBase;
        public final ForgeConfigSpec.IntValue goldDurabilityBase;
        public final ForgeConfigSpec.IntValue ironDurabilityBase;
        public final ForgeConfigSpec.IntValue diamondDurabilityBase;
        public final ForgeConfigSpec.IntValue turtleDurabilityBase;
        public final ForgeConfigSpec.IntValue woodDurabilityBase;
        public final ForgeConfigSpec.IntValue stoneDurabilityBase;
        // 工具耐久公式（默认关闭）：工具耐久 = (护甲耐久基数)^指数 × 修正系数
        public final ForgeConfigSpec.BooleanValue toolDurabilityFormulaEnabled;
        public final ForgeConfigSpec.DoubleValue toolDurabilityExponent;
        // 强制所有材料应用公式：无护甲可依的第三方工具打 warn 提示
        public final ForgeConfigSpec.BooleanValue toolDurabilityForceAllMaterials;

        // 护甲类型系数（AT）：最终耐久 = 耐久基数 × 部位系数 × AT
        public final ForgeConfigSpec.DoubleValue lightArmorCoefficient;
        public final ForgeConfigSpec.DoubleValue mediumArmorCoefficient;
        public final ForgeConfigSpec.DoubleValue heavyArmorCoefficient;

        // 修理效率系数（单个材料修复量 = 耐久基数 × 4 × AT / 修理效率系数）
        public final ForgeConfigSpec.DoubleValue lightRepairEfficiencyCoefficient;
        public final ForgeConfigSpec.DoubleValue mediumRepairEfficiencyCoefficient;
        public final ForgeConfigSpec.DoubleValue heavyRepairEfficiencyCoefficient;
        // 修理量乘数（RepairPerMaterial = 耐久基数 × 乘数 × AT / 修理效率系数）
        public final ForgeConfigSpec.DoubleValue repairMaterialMultiplier;

        // 损坏状态：耐久归零后变为 broken 而非爆掉
        public final ForgeConfigSpec.BooleanValue brokenStateEnabled;
        // 不进入损坏状态的物品（按原版爆掉消失）；物品 ID 列表，默认空 = 所有可损坏物品都会进入损坏状态
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> brokenStateExemptItems;

        // 铁砧修理费用：消耗经验等级 = 消耗材料数（无经验惩罚、可无限修理、不再过于昂贵）
        public final ForgeConfigSpec.BooleanValue anvilRepairCostRule;
        // 过于昂贵阈值（仅当 anvilRepairCostRule=false 时生效；开启移除时不读取）
        public final ForgeConfigSpec.IntValue tooExpensiveThreshold;
        // 铁砧修理花费系数（乘性，per-material 登记优先）：最终费用 = 原费用 × 系数
        public final ForgeConfigSpec.DoubleValue anvilRepairCostCoefficient;

        // 质量系统：玩家质量 = 质量基数 + 最大生命值 × 质量系数；最大负重质量 = 负重系数 × 玩家质量
        public final ForgeConfigSpec.IntValue massBase;
        public final ForgeConfigSpec.DoubleValue massHealthCoefficient;
        public final ForgeConfigSpec.DoubleValue loadFactor;
        // 超重判定系数：超重 ⇔ 穿戴总质量 > 超重系数 × 最大负重质量（即负重比例 > 系数）
        public final ForgeConfigSpec.DoubleValue overloadFactor;
        // 质量派生属性：击退抗性 = min(1.0, 负重比例 × 击退因子)；移速减益 = 负重比例 × 移速因子
        public final ForgeConfigSpec.DoubleValue knockbackResistanceFactor;
        public final ForgeConfigSpec.DoubleValue speedPenaltyFactor;
        // 击退抗性无下限开关（默认开启）：开启时属性无下限，附属模组的负击退抗性修饰符完全生效（击退更远）
        public final ForgeConfigSpec.BooleanValue knockbackResistanceNoLowerLimit;
        // 击退抗性下限（仅当无下限开关关闭时生效）
        public final ForgeConfigSpec.DoubleValue knockbackResistanceMinAllowed;

        // 物品级质量（配置文件版接入质量系统）："<物品 ID>=<质量>" 列表；配置层优先于代码注册与护甲公式
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> itemMassEntries;

        // 材料质量系数（下界合金无配置项，神秘材料运行时固定 0.65）
        public final ForgeConfigSpec.DoubleValue leatherMassCoefficient;
        public final ForgeConfigSpec.DoubleValue turtleMassCoefficient;
        public final ForgeConfigSpec.DoubleValue chainMassCoefficient;
        public final ForgeConfigSpec.DoubleValue ironMassCoefficient;
        public final ForgeConfigSpec.DoubleValue goldMassCoefficient;
        public final ForgeConfigSpec.DoubleValue diamondMassCoefficient;
        // 模组材料质量系数（"<材料名>=<系数>"）：按 ArmorMaterial.getName() 匹配，优先于上面六项与代码登记
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> materialCoefficients;
        // 盔甲纹饰的质量占比：系数 = 护甲材料系数 × (1 - 占比) + 纹饰材料系数 × 占比
        public final ForgeConfigSpec.DoubleValue trimMassWeight;
        // 盔甲纹饰材料系数（"<纹饰材料 ID>=<系数>"）：配置层优先于"同名护甲材料自动跟随"
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> trimCoefficients;

        // 摔落伤害公式：D = (玩家质量 + 护甲质量) × g × (h - 安全高度) / 1920 × 倍率
        public final ForgeConfigSpec.DoubleValue fallGravity;
        public final ForgeConfigSpec.IntValue fallSafeHeight;
        public final ForgeConfigSpec.IntValue fallDamageDenominator;
        public final ForgeConfigSpec.DoubleValue fallJumpSafeGrowth;

        Common(ForgeConfigSpec.Builder builder) {
            // ===== 功能总开关 =====
            // 除「护甲减伤公式」与「护甲耐久损耗公式」这两个核心功能（无可关闭）外，
            // 其余功能均可在此单独关闭。关闭后该功能完全回到原版行为，且其配置项不再被读取。
            builder.push("feature_toggles");
            armorAttributeOverrideEnabled = builder
                    .comment("Armor value / toughness override (entries registered via ArmorAttributeRules / CompatRegistration).")
                    .comment("When false, armor values and toughness stay fully vanilla.")
                    .define("armorAttributeOverrideEnabled", true);
            armorDurabilitySystemEnabled = builder
                    .comment("Armor durability system: max durability = durabilityBase * slotFactor * ArmorTypeCoefficient.")
                    .comment("Covers both vanilla materials and third-party custom materials (derived base).")
                    .comment("When false, every armor piece keeps its vanilla durability.")
                    .define("armorDurabilitySystemEnabled", true);
            unbreakingRedefinitionEnabled = builder
                    .comment("Unbreaking redefinition for armor & shields: deterministic reduction instead of the vanilla per-point chance.")
                    .comment("When false, the vanilla unbreaking behaviour is restored (tools/weapons were never changed).")
                    .define("unbreakingRedefinitionEnabled", true);
            repairAmountFormulaEnabled = builder
                    .comment("Repair amount formula: RepairPerMaterial = durabilityBase * multiplier * AT / repairEfficiencyCoefficient.")
                    .comment("When false, anvil material repair uses the vanilla amount (maxDurability / 4).")
                    .define("repairAmountFormulaEnabled", true);
            massSystemEnabled = builder
                    .comment("Mass system master switch: mass values, load ratio and everything derived from them")
                    .comment("(speed penalty, knockback resistance, mass tooltip) plus the mass term of the fall damage formula.")
                    .comment("When false, every mass-derived effect is disabled AND the fall damage formula uses the")
                    .comment("vanilla-equivalent default mass instead of the real one (so fall damage stays vanilla-like).")
                    .define("massSystemEnabled", true);
            speedPenaltyEnabled = builder
                    .comment("Mass-derived speed penalty. Requires massSystemEnabled.")
                    .comment("When false, the movement speed modifier from load is not applied at all.")
                    .define("speedPenaltyEnabled", true);
            overloadZeroEnabled = builder
                    .comment("Overload punishment. While the player is OVERLOADED (LoadRatio > overloadFactor, see")
                    .comment("player_mass.overloadFactor) these MULTIPLY_TOTAL modifiers are pinned to a huge negative")
                    .comment("value, so each attribute lands on its own 0 floor:")
                    .comment("  - MOVEMENT_SPEED        (ground propulsion 0.216 * Ms / s^3 reads this attribute)")
                    .comment("  - ATTACK_SPEED          (no standing still and trading blows while overloaded)")
                    .comment("  - ForgeMod.SWIM_SPEED   (vanilla water propulsion does NOT read movement speed,")
                    .comment("    this Forge multiplier is the only attribute-side hook for swimming)")
                    .comment("A huge negative rather than -1.0 deliberately: -1.0 only yields 0 if the multiplier zone")
                    .comment("is evaluated as one factor per modifier. With a summed 1 + SUM implementation another")
                    .comment("mod's bonus would cancel it, while a huge negative stays absorbing for any positive")
                    .comment("bonus (and for NaN / Infinity), then the attribute clamp keeps the legal 0 floor.")
                    .comment("Vanilla air control uses the constant flyingSpeed (0.02, 0.026 sprinting) instead of the")
                    .comment("attribute and is deliberately left alone; pushes, vehicles and teleports also pass.")
                    .comment("Independent of speedPenaltyFactor, so overload keeps its cost even when the pack left the")
                    .comment("speed penalty off (default 0.0). Requires massSystemEnabled.")
                    .comment("When false, overload has no effect on movement / attack speed.")
                    .define("overloadZeroEnabled", true);
            knockbackResistanceEnabled = builder
                    .comment("Mass-derived knockback resistance. Requires massSystemEnabled.")
                    .comment("When false, no knockback resistance is granted by the mass system.")
                    .define("knockbackResistanceEnabled", true);
            massTooltipEnabled = builder
                    .comment("Mass / load-ratio lines in item tooltips. Requires massSystemEnabled.")
                    .comment("When false, tooltips show no mass information at all.")
                    .define("massTooltipEnabled", true);
            damageReflectionEnabled = builder
                    .comment("Damage reflection: armor losing durability reflects damage back (the vanilla Thorns")
                    .comment("enchantment is folded into this system and is disabled while this is enabled).")
                    .comment("When false, vanilla Thorns is restored and no reflection from armor wear happens.")
                    .define("damageReflectionEnabled", true);
            fallDamageFormulaEnabled = builder
                    .comment("Fall damage formula: D = (playerMass + wornMass) * g * (h - safeHeight) / denominator * multiplier.")
                    .comment("When false, vanilla fall damage is used (fall_damage_formula options are not read).")
                    .define("fallDamageFormulaEnabled", true);
            experimentalFeaturesEnabled = builder
                    .comment("Master switch for EXPERIMENTAL content. Default FALSE (opt-in) - unlike every other toggle above.")
                    .comment("It gates content whose exact numbers are a MODPACK decision rather than a mechanic, so that")
                    .comment("this mod never decides them for everyone. Currently it gates the armor value / toughness")
                    .comment("re-tables for third-party and vanilla armor sets (see compat.CompatArmorAttributes):")
                    .comment("the numbers there are just the patch author's starting point and are meant to be edited.")
                    .comment("When false (default), every armor piece keeps the values its own mod ships.")
                    .comment("Mechanism adaptations for those mods (armor class, durability base, mass coefficient,")
                    .comment("repair coefficient, tool binding) are NOT gated here: they are built-in content.")
                    .define("experimentalFeaturesEnabled", false);
            builder.pop();

            // ===== 护甲减伤公式（核心功能，不可关闭） =====
            builder.push("armor_damage_reduction_formula");
            lowZoneCoefficient = builder
                    .comment("Coefficient for the low damage zone. L = Armor * coefficient * Toughness")
                    .comment("range={0.0 ~ 10.0}")
                    .defineInRange("lowZoneCoefficient", 0.1, 0.0, 10.0);
            highZoneCoefficient = builder
                    .comment("Coefficient for the high damage zone. H = Armor * (constant + coefficient * Toughness)")
                    .comment("range={0.0 ~ 10.0}")
                    .defineInRange("highZoneCoefficient", 0.1, 0.0, 10.0);
            highZoneConstant = builder
                    .comment("Constant for the high damage zone. H = Armor * (constant + coefficient * Toughness)")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("highZoneConstant", 4.0, 0.0, 100.0);
            armorToughnessMinAllowed = builder
                    .comment("Constant for the min_allowed armor toughness. As you see, it can be negative.")
                    .comment("range={-19 ~ 0}")
                    .defineInRange("armorToughnessMinAllowed", -16, -19, 0);
            maxReduction = builder
                    .comment("Maximum damage reduction in the low damage zone. Reduction = maxReduction when damage <= lowZoneThreshold.")
                    .comment("The high zone reduction scales linearly from maxReduction down to minReduction between the two thresholds.")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("maxReduction", 0.8, 0.0, 1.0);
            minReduction = builder
                    .comment("Minimum damage reduction in the high damage zone (hard-coded to 0.0 before this option existed).")
                    .comment("Reduction = minReduction when damage >= highZoneThreshold: the floor of the formula.")
                    .comment("Values above maxReduction are clamped down to maxReduction (the floor can never exceed the ceiling).")
                    .comment("0.0 (default) reproduces the previous behaviour exactly.")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("minReduction", 0.0, 0.0, 1.0);
            builder.pop();

            // ===== 护甲耐久损耗公式（核心功能，不可关闭） =====
            builder.push("armor_durability_reduction_formula");
            durabilityReductionConstant = builder
                    .comment("Constant for the durability reduction formula. Loss = max( 1, floor( D / (durabilityReductionConstant + ToughnessCoefficient * Toughness) ) )")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("durabilityReductionConstant", 4.0, 0.1, 100.0);
            toughnessCoefficient = builder
                    .comment("Toughness Coefficient for the durability reduction formula. Loss = max( 1, floor( D / (durabilityReductionConstant + ToughnessCoefficient * Toughness) ) )")
                    .comment("range={-100.0 ~ 100.0}")
                    .defineInRange("toughnessCoefficient", 0.2, -100.0, 100.0);
            builder.pop();

            // ===== 耐久附魔重做 =====
            builder.push("the_unbreaking_redefinition");
            unbreakingCoefficient = builder
                    .comment("Coefficient for the unbreaking enchantment redefinition. ReducedAmount = floor( Amount / (1 + UnbreakingCoefficient * UnbreakingLevel) )")
                    .comment("0 = no reduction (vanilla loss); higher = more reduction. Low damage may be fully negated.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("unbreakingCoefficient", 1.0, 0.0, 100.0);
            builder.pop();

            // ===== 反伤（荆棘） =====
            builder.push("damage_reflection");
            thornsCoefficient = builder
                    .comment("Reflection per Thorns level: Reflected = (materialRatio + thornsCoefficient * thornsLevel) * durabilityLoss.")
                    .comment("Default 0.15 (previously a hard-coded 0.6). NO CAP is applied: the total is summed over every")
                    .comment("damaged piece, so a full set of Thorns III (4 pieces) reflects roughly 45% of the incoming")
                    .comment("damage, and modded levels keep scaling linearly.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("thornsCoefficient", 0.15, 0.0, 100.0);
            builder.pop();

            // ===== 部位系数修正（部位基数+制作所需材料个数） =====
            builder.push("armor_slot_factor");
            slotFactorBase = builder
                    .comment("Base for the armor slot durability factor. SlotFactor = base + crafting material count")
                    .comment("head=base+5, chest=base+8, legs=base+7, boots=base+4 (defaults: 13/16/15/12)")
                    .comment("range={0 ~ 32}")
                    .defineInRange("slotFactorBase", 8, 0, 32);
            builder.pop();

            // ===== 耐久基数（对应现实中的莫氏硬度） =====
            // 取值规则：基数 = 4 × 莫氏硬度（金 2.5→10、银 2.75→11、铜 3→12、铁 4→16、
            // 绿宝石 7.5→30、石头 6→24、钻石 10→40；虚构材料按其"设定硬度"折算，
            // 下界合金视为 11.25 → 45，龙钢按"铁的 10 倍硬度"→ 160）。
            // 下界合金无独立配置项：运行时按 钻石 + 金/2 计算
            builder.push("armor_durability_base");
            leatherDurabilityBase = builder
                    .comment("Durability base for leather armor, scaled by Mohs hardness.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("leatherDurabilityBase", 8, 1, 1000);
            goldDurabilityBase = builder
                    .comment("Durability base for gold armor.")
                    .comment("Netherite is derived: diamond + gold / 2.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("goldDurabilityBase", 10, 1, 1000);
            ironDurabilityBase = builder
                    .comment("Durability base for iron armor.")
                    .comment("Chainmail armor reads this value as well, as the vanilla Chainmail armor does.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("ironDurabilityBase", 16, 1, 1000);
            diamondDurabilityBase = builder
                    .comment("Durability base for diamond armor.")
                    .comment("Netherite is derived: diamond + gold / 2.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("diamondDurabilityBase", 40, 1, 1000);
            turtleDurabilityBase = builder
                    .comment("Durability base for turtle shell armor.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("turtleDurabilityBase", 40, 1, 1000);
            woodDurabilityBase = builder
                    .comment("Durability base for wood (planks). Read by the tool durability formula and future wooden armor.")
                    .comment("NOTE: inactive by default! Only read when tool_durability_formula.toolDurabilityFormulaEnabled is true.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("woodDurabilityBase", 7, 1, 1000);
            stoneDurabilityBase = builder
                    .comment("Durability base for stone (cobblestone). Read by the tool durability formula and future stone armor.")
                    .comment("Stone tools apply an extra * 0.25 durability modifier.")
                    .comment("NOTE: inactive by default! Only read when tool_durability_formula.toolDurabilityFormulaEnabled is true.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("stoneDurabilityBase", 24, 1, 1000);
            builder.pop();

            // ===== 工具耐久公式（默认关闭：工具耐久 = (护甲耐久基数)^指数 × 修正系数） =====
            builder.push("tool_durability_formula");
            toolDurabilityFormulaEnabled = builder
                    .comment("When true, tiered tool durability is derived from the armor durability base:")
                    .comment("ToolDurability = (DurabilityBase)^exponent * coefficient.")
                    .comment("Base is bound PER ITEM (see CompatRegistration.registerVanillaTools):")
                    .comment("wood = woodDurabilityBase, stone = stoneDurabilityBase * 0.25,")
                    .comment("iron / gold / diamond = their armor base, netherite = diamond + gold / 2.")
                    .comment("Shears, fishing rods and other non-tiered items keep their own durability (exceptions).")
                    .comment("Default false: vanilla tool durability is used.")
                    .define("toolDurabilityFormulaEnabled", false);
            toolDurabilityExponent = builder
                    .comment("Exponent of the tool durability formula. ToolDurability = (DurabilityBase)^exponent * coefficient.")
                    .comment("range={0.0 ~ 8.0}")
                    .defineInRange("toolDurabilityExponent", 2.0, 0.0, 8.0);
            toolDurabilityForceAllMaterials = builder
                    .comment("When true, the tool durability formula attempts to cover ALL tiered items, including unregistered third-party tools.")
                    .comment("Third-party base comes ONLY from armor-side derivation: bind the tool item to its armor material via ItemDurabilityRules.registerToolArmorMaterial(Item, ArmorMaterial),")
                    .comment("then base = effectiveDurabilityBase (shared with armor; the tool's own getUses() is NOT used).")
                    .comment("Tier-based binding is NOT provided: Tiers are reused across mods, so binding by tier would affect every item sharing that tier.")
                    .comment("Tools with no item-level binding cannot be derived and keep their vanilla durability (a warn is logged once).")
                    .comment("Default false: unregistered/unbound tools quietly keep their vanilla durability.")
                    .define("toolDurabilityForceAllMaterials", false);
            builder.pop();

            // ===== 护甲类型系数（AT：轻甲 0.75 / 中甲 1.0 / 重甲 1.5） =====
            builder.push("armor_type_coefficient");
            lightArmorCoefficient = builder
                    .comment("Armor Type coefficient for light armor (leather, chainmail).")
                    .comment("Durability = base * slotFactor * ArmorTypeCoefficient")
                    .comment("range={0.1 ~ 10.0}")
                    .defineInRange("lightArmorCoefficient", 0.75, 0.1, 10.0);
            mediumArmorCoefficient = builder
                    .comment("Armor Type coefficient for medium armor (gold, iron, diamond, netherite).")
                    .comment("range={0.1 ~ 10.0}")
                    .defineInRange("mediumArmorCoefficient", 1.0, 0.1, 10.0);
            heavyArmorCoefficient = builder
                    .comment("Armor Type coefficient for heavy armor (turtle shell).")
                    .comment("range={0.1 ~ 10.0}")
                    .defineInRange("heavyArmorCoefficient", 1.5, 0.1, 10.0);
            builder.pop();

            // ===== 修理效率系数（轻甲 1.5 / 中甲 1.0 / 重甲 0.75，作公式除数） =====
            builder.push("armor_repair_efficiency_coefficient");
            lightRepairEfficiencyCoefficient = builder
                    .comment("Repair efficiency coefficient for light armor (leather, chainmail).")
                    .comment("RepairPerMaterial = DurabilityBase * 4 * AT / RepairEfficiencyCoefficient")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("lightRepairEfficiencyCoefficient", 1.5, 0.1, 100.0);
            mediumRepairEfficiencyCoefficient = builder
                    .comment("Repair efficiency coefficient for medium armor (gold, iron, diamond, netherite).")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("mediumRepairEfficiencyCoefficient", 1.0, 0.1, 100.0);
            heavyRepairEfficiencyCoefficient = builder
                    .comment("Repair efficiency coefficient for heavy armor (turtle shell).")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("heavyRepairEfficiencyCoefficient", 0.75, 0.1, 100.0);
            repairMaterialMultiplier = builder
                    .comment("Multiplier of the repair amount formula. RepairPerMaterial = DurabilityBase * multiplier * AT / RepairEfficiencyCoefficient.")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("repairMaterialMultiplier", 4.0, 0.1, 100.0);
            builder.pop();

            // ===== 损坏状态（耐久归零后变为 broken 而非爆掉） =====
            builder.push("broken_state");
            brokenStateEnabled = builder
                    .comment("When true, items with zero durability become 'broken' instead of breaking apart.")
                    .comment("Broken items keep their stack, play the break sound & particles, lose all effects (attributes, mining, usage, enchantments) and stay repairable.")
                    .comment("When false, vanilla behavior applies: the item breaks and disappears.")
                    .define("brokenStateEnabled", true);
            brokenStateExemptItems = builder
                    .comment("Items that do NOT enter the broken state: they break and disappear like vanilla.")
                    .comment("Item IDs, e.g. \"minecraft:trident\", \"minecraft:elytra\".")
                    .comment("Empty (default) = every damageable item enters the broken state.")
                    .comment("Only consulted at the moment durability would reach zero, so items already marked")
                    .comment("broken stay broken until repaired: this exemption is NOT retroactive.")
                    .defineListAllowEmpty("brokenStateExemptItems", List.of(),
                            obj -> obj instanceof String s && ResourceLocation.tryParse(s) != null);
            builder.pop();

            // ===== 铁砧修理费用（消耗经验等级 = 消耗材料数） =====
            builder.push("anvil_repair_cost");
            anvilRepairCostRule = builder
                    .comment("When true, anvil repair cost equals the number of consumed materials.")
                    .comment("The accumulated repair penalty (RepairCost) is ignored, so items can be repaired infinitely.")
                    .comment("The 'Too Expensive' cap is removed entirely (tooExpensiveThreshold is ignored).")
                    .define("anvilRepairCostRule", true);
            tooExpensiveThreshold = builder
                    .comment("The 'Too Expensive' threshold in levels. Only read when anvilRepairCostRule is false.")
                    .comment("When anvilRepairCostRule is true this value is not read at all.")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("tooExpensiveThreshold", 40, 1, 1000);
            anvilRepairCostCoefficient = builder
                    .comment("Global anvil repair cost multiplier: finalCost = originalCost * coefficient (default 1.0).")
                    .comment("Per-material overrides can be registered via ArmorRepair.registerRepairCostCoefficient(ArmorMaterial, Double);")
                    .comment("0.0 = free for that material, null = unregister (fall back to this global).")
                    .comment("< 1 repairs are cheaper, > 1 more expensive, 0 makes repairs free.")
                    .comment("range={0.0 ~ 10.0}")
                    .defineInRange("anvilRepairCostCoefficient", 1.0, 0.0, 10.0);
            builder.pop();

            // ===== 质量系统（玩家质量 / 负重） =====
            builder.push("player_mass");
            massBase = builder
                    .comment("Base mass of a player. PlayerMass = massBase + maxHealth * massHealthCoefficient")
                    .comment("range={1 ~ 1000}")
                    .defineInRange("massBase", 50, 1, 1000);
            massHealthCoefficient = builder
                    .comment("Health coefficient of the player mass formula. PlayerMass = massBase + maxHealth * massHealthCoefficient")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("massHealthCoefficient", 0.5, 0.0, 100.0);
            loadFactor = builder
                    .comment("Load factor. MaxLoadMass = loadFactor * PlayerMass. LoadRatio = total armor mass / MaxLoadMass")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("loadFactor", 1.0, 0.1, 100.0);
            overloadFactor = builder
                    .comment("Overload threshold factor. The player is OVERLOADED when")
                    .comment("total worn mass > overloadFactor * MaxLoadMass (equivalently LoadRatio > overloadFactor).")
                    .comment("Default 1.0 = overloaded as soon as the maximum load is exceeded; raise it for a grace band,")
                    .comment("lower it to start punishing before the limit. Overload zeroes movement speed while")
                    .comment("feature_toggles.overloadZeroEnabled is on.")
                    .comment("range={0.1 ~ 100.0}")
                    .defineInRange("overloadFactor", 1.0, 0.1, 100.0);
            knockbackResistanceFactor = builder
                    .comment("Knockback resistance factor. KnockbackResistance = min(1.0, LoadRatio * factor).")
                    .comment("Displayed as 0~100 points. Default 2.4 = full load gives 240 points, capped at 100.")
                    .comment("range={0.0 ~ 10.0}")
                    .defineInRange("knockbackResistanceFactor", 2.4, 0.0, 10.0);
            speedPenaltyFactor = builder
                    .comment("Speed penalty factor. SpeedPenalty = LoadRatio * factor (movement speed reduced by factor at full load).")
                    .comment("DEFAULT 0.0 = the speed penalty is OFF. It was designed for modpacks that trade movement")
                    .comment("speed for damage reduction; on vanilla-style play it feels punishing, hence the default off.")
                    .comment("MODPACK AUTHORS: 0.6 is the intended pack value (-60% movement speed at full load).")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("speedPenaltyFactor", 0.0, 0.0, 1.0);
            knockbackResistanceNoLowerLimit = builder
                    .comment("When true, the knockback_resistance attribute has NO lower limit (negative values fully allowed).")
                    .comment("Interface for addon mods: add a negative modifier to the player's knockback_resistance to make them knocked back further.")
                    .comment("When false, the attribute min is clamped to knockbackResistanceMinAllowed.")
                    .define("knockbackResistanceNoLowerLimit", true);
            knockbackResistanceMinAllowed = builder
                    .comment("Min allowed value of the knockback_resistance attribute (vanilla min is 0).")
                    .comment("Only applied when knockbackResistanceNoLowerLimit is false.")
                    .comment("Negative resistance means being knocked back FURTHER: vanilla formula scales strength by (1 - resistance), e.g. -2.0 = 3x knockback.")
                    .comment("The vanilla formula already supports it; this only widens the attribute clamp so negative modifiers are not zeroed out.")
                    .comment("range={-10000.0 ~ 0.0}")
                    .defineInRange("knockbackResistanceMinAllowed", -2.0, -10000.0, 0.0);
            builder.pop();

            // ===== 物品级质量（配置文件版接入质量系统） =====
            // 配置层优先于代码注册层（PlayerMass.registerItemMass）与护甲材料公式：
            // 整合包/玩家无需写代码即可接入任意物品的质量，或用 0 屏蔽其它模组的登记。
            builder.push("item_mass");
            itemMassEntries = builder
                    .comment("Config-file way to opt items into the mass system: \"<item id>=<mass>\".")
                    .comment("Examples: \"minecraft:trident=8\", \"minecraft:elytra=4\", \"mymod:greatsword=12.5\".")
                    .comment("Priority: this list > PlayerMass.registerItemMass (code) > armor material formula,")
                    .comment("so a value here OVERRIDES whatever a mod (or an addon) registered for that item.")
                    .comment("A value of 0 suppresses the mass of that item entirely (even if a mod registered one).")
                    .comment("Item mass only counts while the item is worn: armor slots, main hand, off hand and")
                    .comment("Curios slots. Items in the backpack never contribute - that is by design.")
                    .comment("Mass is quantized per item to a half step (getItemMassExact: fraction < 0.5 floors,")
                    .comment("fraction >= 0.5 becomes .5), so 0.5 is the smallest positive value that counts.")
                    .comment("Unknown items (mod not installed) and malformed entries are skipped, with a log warning.")
                    .comment("Applied at load time and on config reload; no game restart required.")
                    .defineListAllowEmpty("entries", List.of(),
                            obj -> obj instanceof String s && s.contains("="));
            builder.pop();

            // ===== 材料质量系数（ItemMass = 材料系数 × AT × 护甲值） =====
            // 下界合金无独立配置项：运行时按 (钻石 + 金/2) / 2 计算，再次 / 2 是世界观修正
            builder.push("armor_mass_coefficient");
            trimMassWeight = builder
                    .comment("Share of the TRIM material in the blended mass coefficient of trimmed armor:")
                    .comment("coefficient = armorMaterialCoefficient * (1 - weight) + trimMaterialCoefficient * weight.")
                    .comment("Default 1/6 comes from material counts: a full armor set takes 24 ingots,")
                    .comment("while a trim only uses 4 -> 4 / 24 = 1/6.")
                    .comment("0 disables trim blending entirely (trimmed armor weighs exactly as untrimmed).")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("trimMassWeight", 1.0 / 6.0, 0.0, 1.0);
            trimCoefficients = builder
                    .comment("Mass coefficient of each TRIM material: \"<trim material id>=<coefficient>\".")
                    .comment("Priority: this list > a same-named armor material (auto-follow) > 1.0.")
                    .comment("So an entry here OVERRIDES the auto-follow; delete the entry to get it back.")
                    .comment("Full ids are recommended (\"minecraft:quartz=0.35\"); a bare path (\"quartz=0.35\")")
                    .comment("also matches, which helps when a mod registers its trim in an unexpected namespace.")
                    .comment("On duplicate matches the LAST entry wins.")
                    .comment("Below the armor material coefficient = the trimmed piece gets lighter, above = heavier.")
                    .comment("Negative values are read as 0 (the trim contributes no mass at all).")
                    .comment("Unknown ids (mod not installed) and malformed entries are skipped, with a log warning.")
                    .comment("Defaults are the built-in table: the vanilla non-metal trims plus the two Twilight")
                    .comment("Forest trims that have no same-named armor material (naga_scale, carminite).")
                    .comment("Applied at load time and on config reload; no game restart required.")
                    .defineListAllowEmpty("trim_coefficients",
                            List.of("minecraft:quartz=0.35", "minecraft:redstone=0.2", "minecraft:copper=1.15",
                                    "minecraft:emerald=0.35", "minecraft:lapis=0.4", "minecraft:amethyst=0.3",
                                    "twilightforest:naga_scale=0.65", "twilightforest:carminite=0.1"),
                            obj -> obj instanceof String s && s.contains("="));
            leatherMassCoefficient = builder
                    .comment("Mass coefficient of leather armor. ItemMass = coefficient * AT * Defense")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("leatherMassCoefficient", 0.3, 0.0, 100.0);
            turtleMassCoefficient = builder
                    .comment("Mass coefficient of turtle shell armor.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("turtleMassCoefficient", 0.6, 0.0, 100.0);
            chainMassCoefficient = builder
                    .comment("Mass coefficient of chainmail armor.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("chainMassCoefficient", 1.0, 0.0, 100.0);
            ironMassCoefficient = builder
                    .comment("Mass coefficient of iron armor.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("ironMassCoefficient", 1.0, 0.0, 100.0);
            goldMassCoefficient = builder
                    .comment("Mass coefficient of gold armor.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("goldMassCoefficient", 1.7, 0.0, 100.0);
            diamondMassCoefficient = builder
                    .comment("Mass coefficient of diamond armor.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("diamondMassCoefficient", 0.45, 0.0, 100.0);
            materialCoefficients = builder
                    .comment("Per-MATERIAL mass coefficient override, modded armor materials included:")
                    .comment("\"<armor material name>=<coefficient>\", e.g. \"iceandfire:dragonsteel=1.25\".")
                    .comment("Matched against ArmorMaterial#getName(); both the bare path (\"yeti=0.6\") and the")
                    .comment("namespaced form are accepted, because custom materials do not agree on a convention.")
                    .comment("On duplicate names the LAST entry wins; prefer the namespaced form, a bare path may")
                    .comment("collide across mods.")
                    .comment("Priority: this list > PlayerMass.registerMaterialMassCoefficient (code) > the six")
                    .comment("coefficients above (so they can be overridden from here too, e.g. \"minecraft:iron=0.9\")")
                    .comment("> 1.0 for unregistered custom materials.")
                    .comment("Negative values are read as 0 (a weightless material).")
                    .comment("Entries that match nothing are harmless; malformed entries are skipped with a log warning.")
                    .comment("Applied at load time and on config reload; no game restart required.")
                    .defineListAllowEmpty("material_coefficients", List.of(),
                            obj -> obj instanceof String s && s.contains("="));
            builder.pop();

            // ===== 摔落伤害公式（D = (PlayerMass + WornMass) × g × (h - 安全高度) / 1920） =====
            builder.push("fall_damage_formula");
            fallGravity = builder
                    .comment("Gravity base value (vanilla 0.08 blocks/tick^2). g = value * 400 ≈ 32 m/s^2 in the fall formula.")
                    .comment("Also applied to the player's ForgeMod.ENTITY_GRAVITY attribute, so vanilla gravity follows this value.")
                    .comment("range={0.01 ~ 1.00}")
                    .defineInRange("fallGravity", 0.08, 0.01, 1.00);
            fallSafeHeight = builder
                    .comment("Safe fall height in blocks. No fall damage when h <= safeHeight.")
                    .comment("-1 (default) = follow the vanilla value: 1.20.1 has no safe fall distance attribute,")
                    .comment("so it resolves to the hardcoded vanilla 3 blocks. >= 0 = override it explicitly.")
                    .comment("Same semantics as the 1.21.1 side (there -1 follows the vanilla SAFE_FALL_DISTANCE")
                    .comment("attribute), so config files are interchangeable between the two versions.")
                    .comment("MUST be an integer: a fractional value (e.g. -0.5) counts as an invalid entry and")
                    .comment("is reset to this default with a warning in the log.")
                    .comment("range={-1 ~ 100}")
                    .defineInRange("fallSafeHeight", -1, -1, 100);
            fallDamageDenominator = builder
                    .comment("Denominator of the fall damage formula. 1920 = 60 * 32 (vanilla maxHP=20 player mass 60 * g 32).")
                    .comment("With default values and no armor the formula equals vanilla: D = h - 3. Values <= 0 fall back to 1920.")
                    .comment("range={0 ~ 100000}")
                    .defineInRange("fallDamageDenominator", 1920, 0, 100000);
            fallJumpSafeGrowth = builder
                    .comment("Jump Boost multiplier on safe height. SafeHeight = fallSafeHeight * (1 + fallJumpSafeGrowth * level), level = amplifier + 1.")
                    .comment("Default 0.2 means +20% safe height per Jump Boost level. 0 disables the effect.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("fallJumpSafeGrowth", 0.2, 0.0, 100.0);
            builder.pop();
        }
    }
}
