package dev.newarmorsystem.compat;

import dev.newarmorsystem.api.ArmorClass;
import dev.newarmorsystem.api.CompatRegistration;
import dev.newarmorsystem.api.ItemDurabilityRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * Alex 的生物（Alex's Mobs，modId: {@code alexsmobs}）联动。
 * <p>
 * 职责：把该模组的护甲材料与工具导入新护甲系统（护甲类型、耐久、质量、修理）。
 * <p>
 * <b>结构要点（基于 1.20.1 分支 / 1.22.9 反编译；物品 ID 两侧一致）</b>：
 * <ul>
 *   <li><b>没有完整 4 件套装</b>：该模组的护甲全是<b>单件</b>（每材料仅 1 件），
 *       共 15 件（crocodile_chestplate、roadrunner_boots、centipede_leggings、
 *       moose_headgear、frontier_cap、sombrero、spiked_turtle_shell、fedora、
 *       emu_leggings、tarantula_hawk_elytra、froststalker_helmet、rocky_chestplate、
 *       flying_fish_boots、novelty_hat、unsettling_kimono）。</li>
 *   <li>每个材料是一个独立静态 {@code AMArmorMaterial} 实例且只被 1 件护甲使用，
 *       <b>逐件 getMaterial() 登记即可</b>（无共享，无套装可抽代表件）。</li>
 *   <li>材料槽位表为原版 {@code {13,15,16,11}}（盔/胸/腿/靴），NAS 新部位系数为
 *       {@code {13,16,15,12}}，仅靴子差 1（11 vs 12），未登记基数时反推基本不失真。</li>
 *   <li>工具 3 件（无对应护甲材料）：ghostly_pickaxe、skelewag_sword、tendon_whip，
 *       全部为 {@code Tiers.IRON}（铁级）但耐久自定义，需经
 *       {@link ItemDurabilityRules#registerToolDurabilityBase} 独立登记。
 *       dimensional_carver 为耐久 20 的功能物品（非挖掘工具），不联动。
 *       straddle_helmet / pigshoes 不是 {@code ArmorItem}，跳过。</li>
 *   <li>tarantula_hawk_elytra（狼蛛鹰鞘翅）继承 {@code ArmorItem}（鞘翅），可 getMaterial() 登记。</li>
 * </ul>
 * <p>
 * 本类<b>仅导入内容</b>：所有数值参数均为占位常量（null / ≤0 = 不登记该维度，
 * NAS 自动走默认值或反推，行为安全），由使用者自行填写。
 * <p>
 * 通过注册表按注册 ID 运行时获取物品与材料，<b>无编译期依赖</b>
 * alexsmobs jar；未安装该模组时 {@link #registerAll()} 直接返回，不产生任何影响。
 * <p>
 * 参考（Alex's Mobs 1.22.9）——原版耐久 = multiplier × 槽位表{13,16,15,12}（盔/胸/腿/靴）；
 * 该表由下方逐件耐久反推得到（moose_headgear 247 = 19×13、tarantula_hawk_elytra 144 = 9×16、
 * emu_leggings 135 = 9×15、roadrunner_boots 240 = 20×12），<b>与 NAS 部位系数完全相同</b>。
 * 防御显示顺序为 盔/胸/腿/靴：
 * <ul>
 *   <li>crocodile_chestplate 鳄鱼胸甲    ：×22 → 352，防御 3/7/5/2，附魔 25，韧性 1.0</li>
 *   <li>roadrunner_boots 走鹃靴         ：×20 → 240，防御 1/3/3/1，附魔 20</li>
 *   <li>centipede_leggings 蜈蚣护腿     ：×20 → 300，防御 6/6/6/6，附魔 22，韧性 0.5</li>
 *   <li>moose_headgear 驼鹿头饰        ：×19 → 247，防御 3/3/3/3，附魔 21，韧性 0.5</li>
 *   <li>frontier_cap 边疆帽（浣熊）     ：×17 → 221，防御 3/3/3/3，附魔 21，韧性 2.5</li>
 *   <li>sombrero 宽檐帽                ：×14 → 182，防御 2/2/2/2，附魔 30，韧性 0.5</li>
 *   <li>spiked_turtle_shell 尖刺龟壳   ：×35 → 455，防御 3/3/3/3，附魔 30，韧性 1.0，击退 0.2</li>
 *   <li>fedora 软呢帽                  ：×10 → 130，防御 2/2/2/2，附魔 30，韧性 0.5</li>
 *   <li>emu_leggings 鸸鹋护腿          ：×9  → 135，防御 4/4/4/4，附魔 20，韧性 0.5</li>
 *   <li>tarantula_hawk_elytra 狼蛛鹰鞘翅：×9  → 144，防御 3/3/3/3，附魔 5（鞘翅）</li>
 *   <li>froststalker_helmet 霜行者头盔 ：×9  → 117，防御 3/3/3/3，附魔 15，韧性 0.5</li>
 *   <li>rocky_chestplate 石壳胸甲      ：×20 → 320，防御 3/7/5/2，附魔 10，韧性 0.5</li>
 *   <li>flying_fish_boots 飞鱼靴       ：×9  → 108，防御 1/1/1/1，附魔 8</li>
 *   <li>novelty_hat 新奇帽             ：×10 → 130，防御 2/2/2/2，附魔 30</li>
 *   <li>unsettling_kimono 不安和服     ：×8  → 128，防御 3/3/3/3，附魔 15（可染色）</li>
 * </ul>
 * 工具（均铁级 Tier，原版实际耐久）：
 * <ul>
 *   <li>ghostly_pickaxe 幽灵镐：耐久 700（自定义 getMaxDamage）</li>
 *   <li>skelewag_sword 骷髅剑 ：耐久 250（铁级）</li>
 *   <li>tendon_whip 肌腱鞭    ：耐久 450（自定义 getMaxDamage）</li>
 * </ul>
 *
 * <p><b>1.20.1 → 1.21.1</b>：物品/材料获取改走 {@link BuiltInRegistries#ITEM}
 * （NeoForge 已移除 {@code ForgeRegistries}）；1.21 的护甲材料是注册表条目，
 * 故 {@code ArmorItem#getMaterial()} 需 {@code .value()} 取实例。
 *
 * @author THEREDK
 */
public final class AlexsMobsCompat {

    /** 目标模组的 modId（本类所有注册 ID 都拼在它下面）。 */
    public static final String MOD_ID = "alexsmobs";

    private AlexsMobsCompat() {
    }

    // ============================================================
    // 数值占位（请自行填写；null / ≤0 = 不登记该维度）
    //
    //   armorClass            护甲类型：ArmorClass.LIGHT / MEDIUM / HEAVY；
    //                         不登记时自定义材料默认 MEDIUM
    //   durabilityBase        耐久基数 B（>0 登记，应满足 B = 4 × 莫氏硬度；≤0 不登记，NAS 按原版数值反推）
    //   materialMassCoefficient  材料质量系数（≥0 登记，0 = 零质量；<0 不登记，默认 1.0）
    //   repairCostCoefficient    铁砧修理花费系数（≥0 登记，0 = 免费；<0 不登记，用全局配置）
    //
    // 工具维度（无护甲材料绑定，独立登记）：
    //   toolDurabilityBase    工具耐久基数（>0 登记；≤0 不登记）
    //   toolCoefficient       工具修正系数（>0 登记；≤0 不登记，默认 1.0）
    //
    // 注：该模组护甲为单件且材料不共享，逐件登记；材料自带特殊效果
    // （驼鹿回春、飞鱼滑翔、霜行者冻伤、鞘翅飞行等）不在 NAS 登记范围内。
    // ============================================================

    // ---- 鳄鱼胸甲 CROCODILE_CHESTPLATE ----
    private static final ArmorClass CROCODILE_ARMOR_CLASS = ArmorClass.MEDIUM;
    private static final double CROCODILE_DURABILITY_BASE = 22;
    private static final double CROCODILE_MASS_COEFFICIENT = 0.7;
    private static final double CROCODILE_REPAIR_COEFFICIENT = -1;

    // ---- 走鹃靴 ROADRUNNER_BOOTS ----
    private static final ArmorClass ROADRUNNER_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double ROADRUNNER_DURABILITY_BASE = 20;
    private static final double ROADRUNNER_MASS_COEFFICIENT = 0.45;
    private static final double ROADRUNNER_REPAIR_COEFFICIENT = -1;

    // ---- 蜈蚣护腿 CENTIPEDE_LEGGINGS ----
    private static final ArmorClass CENTIPEDE_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double CENTIPEDE_DURABILITY_BASE = 20;
    private static final double CENTIPEDE_MASS_COEFFICIENT = 0.55;
    private static final double CENTIPEDE_REPAIR_COEFFICIENT = -1;

    // ---- 驼鹿头饰 MOOSE_HEADGEAR ----
    private static final ArmorClass MOOSE_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double MOOSE_DURABILITY_BASE = 19;
    private static final double MOOSE_MASS_COEFFICIENT = 0.75;
    private static final double MOOSE_REPAIR_COEFFICIENT = -1;

    // ---- 边疆帽（浣熊） FRONTIER_CAP ----
    private static final ArmorClass FRONTIER_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double FRONTIER_DURABILITY_BASE = 17;
    private static final double FRONTIER_MASS_COEFFICIENT = 0.4;
    private static final double FRONTIER_REPAIR_COEFFICIENT = -1;

    // ---- 宽檐帽 SOMBRERO ----
    private static final ArmorClass SOMBRERO_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double SOMBRERO_DURABILITY_BASE = 14;
    private static final double SOMBRERO_MASS_COEFFICIENT = 0.4;
    private static final double SOMBRERO_REPAIR_COEFFICIENT = -1;

    // ---- 尖刺龟壳 SPIKED_TURTLE_SHELL ----
    private static final ArmorClass SPIKED_TURTLE_SHELL_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double SPIKED_TURTLE_SHELL_DURABILITY_BASE = 60;
    private static final double SPIKED_TURTLE_SHELL_MASS_COEFFICIENT = 0.75;
    private static final double SPIKED_TURTLE_SHELL_REPAIR_COEFFICIENT = -1;

    // ---- 软呢帽 FEDORA ----
    private static final ArmorClass FEDORA_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double FEDORA_DURABILITY_BASE = 10;
    private static final double FEDORA_MASS_COEFFICIENT = 0.35;
    private static final double FEDORA_REPAIR_COEFFICIENT = -1;

    // ---- 鸸鹋护腿 EMU_LEGGINGS ----
    private static final ArmorClass EMU_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double EMU_DURABILITY_BASE = 9;
    private static final double EMU_MASS_COEFFICIENT = 0.3;
    private static final double EMU_REPAIR_COEFFICIENT = -1;

    // ---- 狼蛛鹰鞘翅 TARANTULA_HAWK_ELYTRA ----
    //      不能削弱鞘翅的耐久~
    private static final ArmorClass TARANTULA_HAWK_ELYTRA_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double TARANTULA_HAWK_ELYTRA_DURABILITY_BASE = 12;
    private static final double TARANTULA_HAWK_ELYTRA_MASS_COEFFICIENT = 0.0;
    private static final double TARANTULA_HAWK_ELYTRA_REPAIR_COEFFICIENT = -1;

    // ---- 霜行者头盔 FROSTSTALKER_HELMET ----
    private static final ArmorClass FROSTSTALKER_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double FROSTSTALKER_DURABILITY_BASE = 9;
    private static final double FROSTSTALKER_MASS_COEFFICIENT = 0.35;
    private static final double FROSTSTALKER_REPAIR_COEFFICIENT = -1;

    // ---- 石壳胸甲 ROCKY_CHESTPLATE ----
    private static final ArmorClass ROCKY_ARMOR_CLASS = ArmorClass.HEAVY;
    private static final double ROCKY_DURABILITY_BASE = 27;
    private static final double ROCKY_MASS_COEFFICIENT = 0.6;
    private static final double ROCKY_REPAIR_COEFFICIENT = -1;

    // ---- 飞鱼靴 FLYING_FISH_BOOTS ----
    private static final ArmorClass FLYING_FISH_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double FLYING_FISH_DURABILITY_BASE = 9;
    private static final double FLYING_FISH_MASS_COEFFICIENT = 0.45;
    private static final double FLYING_FISH_REPAIR_COEFFICIENT = -1;

    // ---- 新奇帽 NOVELTY_HAT ----
    private static final ArmorClass NOVELTY_HAT_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double NOVELTY_HAT_DURABILITY_BASE = 10;
    private static final double NOVELTY_HAT_MASS_COEFFICIENT = 0.3;
    private static final double NOVELTY_HAT_REPAIR_COEFFICIENT = -1;

    // ---- 不安和服 UNSETTLING_KIMONO ----
    private static final ArmorClass KIMONO_ARMOR_CLASS = ArmorClass.LIGHT;
    private static final double KIMONO_DURABILITY_BASE = 8;
    private static final double KIMONO_MASS_COEFFICIENT = 0.3;
    private static final double KIMONO_REPAIR_COEFFICIENT = -1;

    // ---- 工具（无护甲材料绑定，独立登记） ----
    private static final double GHOSTLY_PICKAXE_TOOL_BASE = -1;
    private static final double GHOSTLY_PICKAXE_TOOL_COEFFICIENT = -1;

    private static final double SKELEWAG_SWORD_TOOL_BASE = -1;
    private static final double SKELEWAG_SWORD_TOOL_COEFFICIENT = -1;

    private static final double TENDON_WHIP_TOOL_BASE = -1;
    private static final double TENDON_WHIP_TOOL_COEFFICIENT = -1;

    // ============================================================================
    // 护甲值 / 盔甲韧性（数值改写维度）—— 经 CompatArmorAttributes 门控：
    // feature_toggles.experimentalFeaturesEnabled，默认关闭时完全不生效。
    //
    // 数组顺序固定 = {头盔, 胸甲, 护腿, 靴子}；韧性四部位统一（未单列韧性的件 = 0）。支持小数。
    // 注意语义：与上方"≤ 0 = 不登记"不同，这里 0 = 该部位【移除】护甲值/韧性（NAS 语义）。
    //
    // 数值取自类注释的"防御 盔/胸/腿/靴"参考列（= 各件原版数值）。Alex's Mobs 每件护甲
    // 各有独立材料（见"逐件注册"注释），故这里逐件登记；同件材料自带的击退抗性不在此登记
    // （那是另一条属性，由质量系统统一供给）。
    // ============================================================================

    /** 鳄鱼胸甲护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] CROCODILE_ARMOR = {3, 8, 5, 2};
    /** 鳄鱼胸甲盔甲韧性。 */
    private static final double CROCODILE_TOUGHNESS = 2.0;

    /** 走鹃靴护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] ROADRUNNER_ARMOR = {1.5, 3, 3, 1.5};
    /** 走鹃靴盔甲韧性。 */
    private static final double ROADRUNNER_TOUGHNESS = 0.0;

    /** 蜈蚣护腿护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] CENTIPEDE_ARMOR = {3, 3, 3, 3};
    /** 蜈蚣护腿盔甲韧性。 */
    private static final double CENTIPEDE_TOUGHNESS = 0.5;

    /** 驼鹿头饰护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] MOOSE_ARMOR = {4, 4, 4, 4};
    /** 驼鹿头饰盔甲韧性。 */
    private static final double MOOSE_TOUGHNESS = 1.5;

    /** 边疆帽护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] FRONTIER_ARMOR = {2.5, 2.5, 2.5, 2.5};
    /** 边疆帽盔甲韧性。 */
    private static final double FRONTIER_TOUGHNESS = 0.0;

    /** 宽檐帽护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] SOMBRERO_ARMOR = {2, 2, 2, 2};
    /** 宽檐帽盔甲韧性。 */
    private static final double SOMBRERO_TOUGHNESS = 0.0;

    /** 尖刺龟壳护甲值 {头盔, 胸甲, 护腿, 靴子} */
    private static final double[] SPIKED_TURTLE_SHELL_ARMOR = {4.5, 4.5, 4.5, 4.5};
    /** 尖刺龟壳盔甲韧性（基底即海龟壳 → 对齐 NAS 海龟套的 3.0）。 */
    private static final double SPIKED_TURTLE_SHELL_TOUGHNESS = 3.0;

    /** 软呢帽护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] FEDORA_ARMOR = {2, 2, 2, 2};
    /** 软呢帽盔甲韧性。 */
    private static final double FEDORA_TOUGHNESS = 0.0;

    /** 鸸鹋护腿护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] EMU_ARMOR = {4, 4, 4, 4};
    /** 鸸鹋护腿盔甲韧性。 */
    private static final double EMU_TOUGHNESS = 0.0;

    /** 狼蛛鹰鞘翅护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] TARANTULA_HAWK_ELYTRA_ARMOR = {2, 2, 2, 2};
    /** 狼蛛鹰鞘翅盔甲韧性。 */
    private static final double TARANTULA_HAWK_ELYTRA_TOUGHNESS = 0.0;

    /** 霜行者头盔护甲值 {头盔, 胸甲, 护腿, 靴子}（该件只占头盔位；头 4 = "小型龙"的鳞甲档，对齐骑士金属/幻影）。 */
    private static final double[] FROSTSTALKER_ARMOR = {4, 4, 4, 4};
    /** 霜行者头盔盔甲韧性（龙鳞：硬且韧，对齐暮色鳞甲档）。 */
    private static final double FROSTSTALKER_TOUGHNESS = 2.0;

    /** 石壳胸甲护甲值 {头盔, 胸甲, 护腿, 靴子}（该件只占胸甲位；胸 11.5 = 居中于圆石套 9.5 与深板岩套 14）。 */
    private static final double[] ROCKY_ARMOR = {3, 12, 5, 2};
    /** 石壳胸甲盔甲韧性（石头：居中于圆石 −2 与深板岩 −3；硬而脆，与海晶 −2.0 同思路）。 */
    private static final double ROCKY_TOUGHNESS = -2.5;

    /** 飞鱼靴护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] FLYING_FISH_ARMOR = {1, 1, 1, 1};
    /** 飞鱼靴盔甲韧性。 */
    private static final double FLYING_FISH_TOUGHNESS = 0.0;

    /** 新奇帽护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] NOVELTY_HAT_ARMOR = {2, 2, 2, 2};
    /** 新奇帽盔甲韧性。 */
    private static final double NOVELTY_HAT_TOUGHNESS = 0.0;

    /** 不安和服护甲值 {头盔, 胸甲, 护腿, 靴子}。 */
    private static final double[] KIMONO_ARMOR = {3, 3, 3, 3};
    /** 不安和服（胸甲栏位）盔甲韧性。 */
    private static final double KIMONO_TOUGHNESS = 0.0;

    // ============================================================
    // 主入口
    // ============================================================

    /** 由 CompatModules 在 commonSetup 中调用。未安装 Alex's Mobs 时无操作。 */
    public static void registerAll() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        registerArmor("crocodile_chestplate", CROCODILE_ARMOR_CLASS, CROCODILE_DURABILITY_BASE,
                CROCODILE_MASS_COEFFICIENT, CROCODILE_REPAIR_COEFFICIENT,
                CROCODILE_ARMOR, CROCODILE_TOUGHNESS);
        registerArmor("roadrunner_boots", ROADRUNNER_ARMOR_CLASS, ROADRUNNER_DURABILITY_BASE,
                ROADRUNNER_MASS_COEFFICIENT, ROADRUNNER_REPAIR_COEFFICIENT,
                ROADRUNNER_ARMOR, ROADRUNNER_TOUGHNESS);
        registerArmor("centipede_leggings", CENTIPEDE_ARMOR_CLASS, CENTIPEDE_DURABILITY_BASE,
                CENTIPEDE_MASS_COEFFICIENT, CENTIPEDE_REPAIR_COEFFICIENT,
                CENTIPEDE_ARMOR, CENTIPEDE_TOUGHNESS);
        registerArmor("moose_headgear", MOOSE_ARMOR_CLASS, MOOSE_DURABILITY_BASE,
                MOOSE_MASS_COEFFICIENT, MOOSE_REPAIR_COEFFICIENT,
                MOOSE_ARMOR, MOOSE_TOUGHNESS);
        registerArmor("frontier_cap", FRONTIER_ARMOR_CLASS, FRONTIER_DURABILITY_BASE,
                FRONTIER_MASS_COEFFICIENT, FRONTIER_REPAIR_COEFFICIENT,
                FRONTIER_ARMOR, FRONTIER_TOUGHNESS);
        registerArmor("sombrero", SOMBRERO_ARMOR_CLASS, SOMBRERO_DURABILITY_BASE,
                SOMBRERO_MASS_COEFFICIENT, SOMBRERO_REPAIR_COEFFICIENT,
                SOMBRERO_ARMOR, SOMBRERO_TOUGHNESS);
        registerArmor("spiked_turtle_shell", SPIKED_TURTLE_SHELL_ARMOR_CLASS,
                SPIKED_TURTLE_SHELL_DURABILITY_BASE, SPIKED_TURTLE_SHELL_MASS_COEFFICIENT,
                SPIKED_TURTLE_SHELL_REPAIR_COEFFICIENT,
                SPIKED_TURTLE_SHELL_ARMOR, SPIKED_TURTLE_SHELL_TOUGHNESS);
        registerArmor("fedora", FEDORA_ARMOR_CLASS, FEDORA_DURABILITY_BASE,
                FEDORA_MASS_COEFFICIENT, FEDORA_REPAIR_COEFFICIENT,
                FEDORA_ARMOR, FEDORA_TOUGHNESS);
        registerArmor("emu_leggings", EMU_ARMOR_CLASS, EMU_DURABILITY_BASE,
                EMU_MASS_COEFFICIENT, EMU_REPAIR_COEFFICIENT,
                EMU_ARMOR, EMU_TOUGHNESS);
        registerArmor("tarantula_hawk_elytra", TARANTULA_HAWK_ELYTRA_ARMOR_CLASS,
                TARANTULA_HAWK_ELYTRA_DURABILITY_BASE, TARANTULA_HAWK_ELYTRA_MASS_COEFFICIENT,
                TARANTULA_HAWK_ELYTRA_REPAIR_COEFFICIENT,
                TARANTULA_HAWK_ELYTRA_ARMOR, TARANTULA_HAWK_ELYTRA_TOUGHNESS);
        registerArmor("froststalker_helmet", FROSTSTALKER_ARMOR_CLASS, FROSTSTALKER_DURABILITY_BASE,
                FROSTSTALKER_MASS_COEFFICIENT, FROSTSTALKER_REPAIR_COEFFICIENT,
                FROSTSTALKER_ARMOR, FROSTSTALKER_TOUGHNESS);
        registerArmor("rocky_chestplate", ROCKY_ARMOR_CLASS, ROCKY_DURABILITY_BASE,
                ROCKY_MASS_COEFFICIENT, ROCKY_REPAIR_COEFFICIENT,
                ROCKY_ARMOR, ROCKY_TOUGHNESS);
        registerArmor("flying_fish_boots", FLYING_FISH_ARMOR_CLASS, FLYING_FISH_DURABILITY_BASE,
                FLYING_FISH_MASS_COEFFICIENT, FLYING_FISH_REPAIR_COEFFICIENT,
                FLYING_FISH_ARMOR, FLYING_FISH_TOUGHNESS);
        registerArmor("novelty_hat", NOVELTY_HAT_ARMOR_CLASS, NOVELTY_HAT_DURABILITY_BASE,
                NOVELTY_HAT_MASS_COEFFICIENT, NOVELTY_HAT_REPAIR_COEFFICIENT,
                NOVELTY_HAT_ARMOR, NOVELTY_HAT_TOUGHNESS);
        registerArmor("unsettling_kimono", KIMONO_ARMOR_CLASS, KIMONO_DURABILITY_BASE,
                KIMONO_MASS_COEFFICIENT, KIMONO_REPAIR_COEFFICIENT,
                KIMONO_ARMOR, KIMONO_TOUGHNESS);

        registerTool("ghostly_pickaxe", GHOSTLY_PICKAXE_TOOL_BASE, GHOSTLY_PICKAXE_TOOL_COEFFICIENT);
        registerTool("skelewag_sword", SKELEWAG_SWORD_TOOL_BASE, SKELEWAG_SWORD_TOOL_COEFFICIENT);
        registerTool("tendon_whip", TENDON_WHIP_TOOL_BASE, TENDON_WHIP_TOOL_COEFFICIENT);
    }

    // ============================================================
    // 逐件注册（每材料仅 1 件护甲，无共享；工具位传空，工具独立登记）
    // ============================================================

    /**
     * 登记单件护甲：机制适配（分类/耐久基数/质量/修理 —— 始终生效）
     * + 数值改写（护甲值/韧性 —— 经 {@link CompatArmorAttributes} 门控）。
     *
     * @param piecePath 护甲件注册名（用于运行时反查其材料实例）
     * @param clazz     护甲类型
     * @param base      耐久基数（≤ 0 = 不登记，按物品原版耐久反推）
     * @param mass      材料质量系数（≤ 0 = 不登记，默认 1.0）
     * @param repair    铁砧修理花费系数（≤ 0 = 不登记，用全局配置）
     * @param armor     {头盔, 胸甲, 护腿, 靴子} 四部位护甲值
     * @param toughness 四部位统一的盔甲韧性
     */
    private static void registerArmor(String piecePath, ArmorClass clazz, double base,
                                      double mass, double repair, double[] armor, double toughness) {
        ArmorMaterial material = armorMaterialOf(piecePath);
        if (material == null) {
            return;
        }
        CompatRegistration.registerMaterialRulesForTools(
                material, clazz, base, mass, repair, -1);
        registerArmorAttributes(material, armor, toughness);
    }

    /**
     * 按材料 × 部位登记四个部位的护甲值与统一韧性（数值改写维度）。
     *
     * <p>经 {@link CompatArmorAttributes} 转发，因此受
     * {@code feature_toggles.experimentalFeaturesEnabled}（默认关闭）门控 ——
     * 关闭时不写任何值，各护甲保持 Alex's Mobs 自己的数值。
     *
     * @param material  护甲材料实例
     * @param armor     {头盔, 胸甲, 护腿, 靴子} 四部位护甲值（0 = 该部位不提供）
     * @param toughness 四部位统一的盔甲韧性（0 = 不提供）
     */
    private static void registerArmorAttributes(ArmorMaterial material, double[] armor, double toughness) {
        ArmorItem.Type[] types = {
                ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
        };
        for (int i = 0; i < 4; i++) {
            CompatArmorAttributes.register(material, types[i], armor[i], toughness);
        }
    }

    private static void registerTool(String path, double base, double coefficient) {
        Item tool = item(path);
        if (tool == null) {
            return;
        }
        // base ≤ 0 不登记；coefficient ≤ 0 不登记（该重载内部已做过滤）
        ItemDurabilityRules.registerToolDurabilityBase(tool, base, coefficient);
    }

    // ============================================================
    // 辅助：按注册 ID 运行时获取物品 / 护甲材料（无编译期依赖）
    // ============================================================

    private static Item item(String path) {
        // ITEM 是默认值注册表：get() 未命中返回 Items.AIR（非 null），故须 containsKey 判存在，
        // 否则调用方的 null 守卫失效、规则会被静默登记到 air 上。
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    private static ArmorMaterial armorMaterialOf(String piecePath) {
        Item i = item(piecePath);
        if (i instanceof ArmorItem armor) {
            // 1.21：材料是注册表条目，需取 value() 得到实例
            return armor.getMaterial().value();
        }
        return null;
    }
}
