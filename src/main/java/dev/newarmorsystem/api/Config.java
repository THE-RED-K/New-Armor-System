package dev.newarmorsystem.api;


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
        public final ForgeConfigSpec.DoubleValue lowZoneCoefficient;
        public final ForgeConfigSpec.DoubleValue highZoneCoefficient;
        public final ForgeConfigSpec.DoubleValue highZoneConstant;
        public final ForgeConfigSpec.IntValue armorToughnessMinAllowed;
        public final ForgeConfigSpec.DoubleValue maxReduction;
        public final ForgeConfigSpec.DoubleValue durabilityReductionConstant;
        public final ForgeConfigSpec.DoubleValue toughnessCoefficient;
        public final ForgeConfigSpec.DoubleValue unbreakingCoefficient;
        public final ForgeConfigSpec.IntValue slotFactorBase;
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
        // 质量派生属性：击退抗性 = min(1.0, 负重比例 × 击退因子)；移速减益 = 负重比例 × 移速因子
        public final ForgeConfigSpec.DoubleValue knockbackResistanceFactor;
        public final ForgeConfigSpec.DoubleValue speedPenaltyFactor;
        // 击退抗性无下限开关（默认开启）：开启时属性无下限，附属模组的负击退抗性修饰符完全生效（击退更远）
        public final ForgeConfigSpec.BooleanValue knockbackResistanceNoLowerLimit;
        // 击退抗性下限（仅当无下限开关关闭时生效）
        public final ForgeConfigSpec.DoubleValue knockbackResistanceMinAllowed;

        // 材料质量系数（下界合金无配置项，神秘材料运行时固定 0.65）
        public final ForgeConfigSpec.DoubleValue leatherMassCoefficient;
        public final ForgeConfigSpec.DoubleValue turtleMassCoefficient;
        public final ForgeConfigSpec.DoubleValue chainMassCoefficient;
        public final ForgeConfigSpec.DoubleValue ironMassCoefficient;
        public final ForgeConfigSpec.DoubleValue goldMassCoefficient;
        public final ForgeConfigSpec.DoubleValue diamondMassCoefficient;

        // 摔落伤害公式：D = (玩家质量 + 护甲质量) × g × (h - 安全高度) / 1920 × 倍率
        public final ForgeConfigSpec.DoubleValue fallGravity;
        public final ForgeConfigSpec.IntValue fallSafeHeight;
        public final ForgeConfigSpec.IntValue fallDamageDenominator;
        public final ForgeConfigSpec.DoubleValue fallJumpSafeGrowth;

        Common(ForgeConfigSpec.Builder builder) {
            // ===== 护甲减伤公式 =====
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
                    .comment("The high zone reduction scales linearly from maxReduction down to 0 between the two thresholds.")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("maxReduction", 0.8, 0.0, 1.0);
            builder.pop();

            // ===== 护甲耐久损耗公式 =====
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

            // ===== 部位系数修正（部位基数+制作所需材料个数） =====
            builder.push("armor_slot_factor");
            slotFactorBase = builder
                    .comment("Base for the armor slot durability factor. SlotFactor = base + crafting material count")
                    .comment("head=base+5, chest=base+8, legs=base+7, boots=base+4 (defaults: 13/16/15/12)")
                    .comment("range={0 ~ 32}")
                    .defineInRange("slotFactorBase", 8, 0, 32);
            builder.pop();

            // ===== 耐久基数（对应现实中的莫氏硬度） =====
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
            knockbackResistanceFactor = builder
                    .comment("Knockback resistance factor. KnockbackResistance = min(1.0, LoadRatio * factor).")
                    .comment("Displayed as 0~100 points. Default 1.2 = full load gives 120 points, capped at 100.")
                    .comment("range={0.0 ~ 10.0}")
                    .defineInRange("knockbackResistanceFactor", 1.2, 0.0, 10.0);
            speedPenaltyFactor = builder
                    .comment("Speed penalty factor. SpeedPenalty = LoadRatio * factor (movement speed reduced by factor at full load).")
                    .comment("Default 0.6 = -60% movement speed at full load.")
                    .comment("range={0.0 ~ 1.0}")
                    .defineInRange("speedPenaltyFactor", 0.6, 0.0, 1.0);
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

            // ===== 材料质量系数（ItemMass = 材料系数 × AT × 护甲值） =====
            // 下界合金无独立配置项：运行时按 (钻石 + 金/2) / 2 计算，再次 / 2 是世界观修正
            builder.push("armor_mass_coefficient");
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
                    .comment("range={0 ~ 100}")
                    .defineInRange("fallSafeHeight", 3, 0, 100);
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
