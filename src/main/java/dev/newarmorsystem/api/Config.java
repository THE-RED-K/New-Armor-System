package dev.newarmorsystem.api;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置（NeoForge {@link ModConfigSpec}）。
 *
 * <p>当前仅包含<b>护甲减伤公式</b>相关配置项；其余子系统（耐久损耗、耐久附魔、质量、摔落伤害、
 * 铁砧修理、损坏状态、工具耐久等）的配置项将在各自移植时逐步补入。
 */
public class Config {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {
        public final ModConfigSpec.DoubleValue lowZoneCoefficient;
        public final ModConfigSpec.DoubleValue highZoneCoefficient;
        public final ModConfigSpec.DoubleValue highZoneConstant;
        public final ModConfigSpec.IntValue armorToughnessMinAllowed;
        public final ModConfigSpec.DoubleValue maxReduction;
        public final ModConfigSpec.DoubleValue durabilityReductionConstant;
        public final ModConfigSpec.DoubleValue toughnessCoefficient;
        public final ModConfigSpec.DoubleValue unbreakingCoefficient;

        // 部位系数修正（部位基数 + 制作所需材料个数）
        public final ModConfigSpec.IntValue slotFactorBase;

        // 耐久基数（对应现实中的莫氏硬度）
        public final ModConfigSpec.IntValue leatherDurabilityBase;
        public final ModConfigSpec.IntValue goldDurabilityBase;
        public final ModConfigSpec.IntValue ironDurabilityBase;
        public final ModConfigSpec.IntValue diamondDurabilityBase;
        public final ModConfigSpec.IntValue turtleDurabilityBase;
        public final ModConfigSpec.IntValue woodDurabilityBase;
        public final ModConfigSpec.IntValue stoneDurabilityBase;

        // 工具耐久公式（默认关闭）：工具耐久 = (护甲耐久基数)^指数 × 修正系数
        public final ModConfigSpec.BooleanValue toolDurabilityFormulaEnabled;
        public final ModConfigSpec.DoubleValue toolDurabilityExponent;
        // 强制所有材料应用公式：无护甲可依的第三方工具打 warn 提示
        public final ModConfigSpec.BooleanValue toolDurabilityForceAllMaterials;

        // 护甲类型系数（AT）
        public final ModConfigSpec.DoubleValue lightArmorCoefficient;
        public final ModConfigSpec.DoubleValue mediumArmorCoefficient;
        public final ModConfigSpec.DoubleValue heavyArmorCoefficient;

        // 修理效率系数
        public final ModConfigSpec.DoubleValue lightRepairEfficiencyCoefficient;
        public final ModConfigSpec.DoubleValue mediumRepairEfficiencyCoefficient;
        public final ModConfigSpec.DoubleValue heavyRepairEfficiencyCoefficient;
        public final ModConfigSpec.DoubleValue repairMaterialMultiplier;

        // 铁砧修理费用：消耗经验等级 = 消耗材料数（无经验惩罚、可无限修理、不再过于昂贵）
        public final ModConfigSpec.BooleanValue anvilRepairCostRule;
        public final ModConfigSpec.IntValue tooExpensiveThreshold;
        public final ModConfigSpec.DoubleValue anvilRepairCostCoefficient;

        // 质量系统：玩家质量 = 质量基数 + 最大生命值 × 质量系数；最大负重质量 = 负重系数 × 玩家质量
        public final ModConfigSpec.IntValue massBase;
        public final ModConfigSpec.DoubleValue massHealthCoefficient;
        public final ModConfigSpec.DoubleValue loadFactor;
        public final ModConfigSpec.DoubleValue knockbackResistanceFactor;
        public final ModConfigSpec.DoubleValue speedPenaltyFactor;
        public final ModConfigSpec.BooleanValue knockbackResistanceNoLowerLimit;
        public final ModConfigSpec.DoubleValue knockbackResistanceMinAllowed;

        // 材料质量系数（下界合金无配置项，运行时推导）
        public final ModConfigSpec.DoubleValue leatherMassCoefficient;
        public final ModConfigSpec.DoubleValue turtleMassCoefficient;
        public final ModConfigSpec.DoubleValue chainMassCoefficient;
        public final ModConfigSpec.DoubleValue ironMassCoefficient;
        public final ModConfigSpec.DoubleValue goldMassCoefficient;
        public final ModConfigSpec.DoubleValue diamondMassCoefficient;

        // 摔落伤害公式（安全高度主源为原版属性 SAFE_FALL_DISTANCE，fallSafeHeight 可覆写其基础值）
        public final ModConfigSpec.DoubleValue fallGravity;
        public final ModConfigSpec.IntValue fallSafeHeight;
        public final ModConfigSpec.IntValue fallDamageDenominator;
        public final ModConfigSpec.DoubleValue fallJumpSafeGrowth;

        // 损坏状态：耐久归零后变为 broken 而非爆掉
        public final ModConfigSpec.BooleanValue brokenStateEnabled;

        Common(ModConfigSpec.Builder builder) {
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

            // ===== 损坏状态（耐久归零后变为 broken 而非爆掉） =====
            builder.push("broken_state");
            brokenStateEnabled = builder
                    .comment("When true, items with zero durability become 'broken' instead of breaking apart.")
                    .comment("Broken items keep their stack, play the break sound & particles, lose all effects (attributes, mining, usage, enchantments) and stay repairable.")
                    .comment("When false, vanilla behavior applies: the item breaks and disappears.")
                    .define("brokenStateEnabled", true);
            builder.pop();

            // ===== 耐久附魔重做 =====
            builder.push("the_unbreaking_redefinition");
            unbreakingCoefficient = builder
                    .comment("Coefficient for the unbreaking enchantment redefinition. ReducedAmount = floor( Amount / (1 + UnbreakingCoefficient * UnbreakingLevel) )")
                    .comment("0 = no reduction (vanilla loss); higher = more reduction. Low damage may be fully negated.")
                    .comment("Only applies to armor and shields; other items keep vanilla probabilistic reduction.")
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
            // 结果在 ModifyDefaultComponentsEvent 阶段烘焙进 DataComponents.MAX_DAMAGE，
            // 故改动本分组需重启游戏才生效（与护甲耐久基数配置的行为一致）。
            builder.push("tool_durability_formula");
            toolDurabilityFormulaEnabled = builder
                    .comment("When true, tiered tool durability is derived from the armor durability base:")
                    .comment("ToolDurability = (DurabilityBase)^exponent * coefficient.")
                    .comment("Base is bound PER ITEM (see CompatRegistration.registerVanillaTools):")
                    .comment("wood = woodDurabilityBase, stone = stoneDurabilityBase * 0.25,")
                    .comment("iron / gold / diamond = their armor base, netherite = diamond + gold / 2.")
                    .comment("Shears, fishing rods and other non-tiered items keep their own durability (exceptions).")
                    .comment("The value is baked into MAX_DAMAGE at load time, so a game restart is required after changing it.")
                    .comment("Default false: vanilla tool durability is used.")
                    .define("toolDurabilityFormulaEnabled", false);
            toolDurabilityExponent = builder
                    .comment("Exponent of the tool durability formula. ToolDurability = (DurabilityBase)^exponent * coefficient.")
                    .comment("range={0.0 ~ 8.0}")
                    .defineInRange("toolDurabilityExponent", 2.0, 0.0, 8.0);
            toolDurabilityForceAllMaterials = builder
                    .comment("When true, the tool durability formula warns about tiered items that have no item-level registration.")
                    .comment("Third-party base comes ONLY from armor-side derivation: bind the tool item to its armor material via")
                    .comment("ItemDurabilityRules.registerToolArmorMaterial(Item, ArmorMaterial),")
                    .comment("then base = effectiveDurabilityBase (shared with armor; the tool's own getUses() is NOT used).")
                    .comment("Tier-based binding is NOT provided: Tiers are reused across mods, so binding by tier would affect every item sharing that tier.")
                    .comment("Tools with no item-level binding cannot be derived and keep their vanilla durability (a warn is logged once).")
                    .define("toolDurabilityForceAllMaterials", false);
            builder.pop();

            // ===== 护甲类型系数（AT：轻甲 0.75 / 中甲 1.0 / 重甲 1.5） =====
            builder.push("armor_type_coefficient");
            lightArmorCoefficient = builder
                    .comment("Armor Type coefficient for light armor (leather, chainmail).")
                    .comment("Durability = slotFactor * base * ArmorTypeCoefficient")
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

            // ===== 铁砧修理费用（消耗经验等级 = 消耗材料数） =====
            builder.push("anvil_repair_cost");
            anvilRepairCostRule = builder
                    .comment("When true, anvil repair cost equals the number of consumed materials.")
                    .comment("The accumulated repair penalty (DataComponents.REPAIR_COST) is ignored, so items can be repaired infinitely.")
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
                    .comment("Gravity base value (vanilla 0.08 blocks/tick^2). g = value * 400 ~ 32 m/s^2 in the fall formula.")
                    .comment("Also applied to the player's Attributes.GRAVITY, so vanilla gravity follows this value.")
                    .comment("range={0.01 ~ 1.00}")
                    .defineInRange("fallGravity", 0.08, 0.01, 1.00);
            fallSafeHeight = builder
                    .comment("Optional OVERRIDE of the base value of the vanilla safe fall distance attribute, in blocks.")
                    .comment("-1 (default) = do NOT override: follow the attribute as-is (vanilla 3.0),")
                    .comment("  so safe fall distance changes made by other mods are respected.")
                    .comment(">= 0 = force the player's minecraft:safe_fall_distance base value to this value.")
                    .comment("Jump Boost then scales multiplicatively off this base: value * (1 + fallJumpSafeGrowth * level).")
                    .comment("Note: attribute base values are persisted in the player data, so setting this back to -1")
                    .comment("  does not revert an already applied override.")
                    .comment("range={-1 ~ 100}")
                    .defineInRange("fallSafeHeight", -1, -1, 100);
            fallDamageDenominator = builder
                    .comment("Denominator of the fall damage formula. 1920 = 60 * 32 (vanilla maxHP=20 player mass 60 * g 32).")
                    .comment("With default values and no armor the formula equals vanilla: D = h - 3. Values <= 0 fall back to 1920.")
                    .comment("range={0 ~ 100000}")
                    .defineInRange("fallDamageDenominator", 1920, 0, 100000);
            fallJumpSafeGrowth = builder
                    .comment("Per-level growth of Jump Boost, applied MULTIPLICATIVELY to the safe fall distance.")
                    .comment("SafeDistance = BASE * (1 + fallJumpSafeGrowth * level), level = amplifier + 1.")
                    .comment("BASE is the minecraft:safe_fall_distance attribute base value: vanilla 3.0, unless overridden by fallSafeHeight.")
                    .comment("This replaces vanilla's linear +1 block per Jump Boost level. Default 0.2 means +20% per level; 0 disables it.")
                    .comment("range={0.0 ~ 100.0}")
                    .defineInRange("fallJumpSafeGrowth", 0.2, 0.0, 100.0);
            builder.pop();
        }
    }
}
