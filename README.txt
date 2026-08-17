新护甲系统（New Armor System）

本模组彻底重构了护甲系统：护甲的减伤不再只是固定百分比，而是根据伤害大小分段生效。护甲会随战斗损耗，但你可以无限次修理它们。穿戴不同护甲会影响你的移动速度和摔落伤害——每一件护甲都有它自己的物理属性。
本模组不添加新护甲、新附魔或新物品。未来可能会开发附属模组，届时将在此基础上添加具体装备等内容。
设计理念：让每一件护甲都有清晰可感知的定位，让护甲选择成为有意义的策略决策，而非单纯比较减伤数字。

护甲减伤

- 减伤不再是简单的百分比，而是三段式规则：
  - 低伤区：伤害低于护甲承受区间时，护甲80%减伤；
  - 中伤区：减伤随伤害升高线性衰减；
  - 高伤区：护甲完全失效，0%减伤。
- 盔甲韧性的作用被强化：韧性与护甲值共同决定护甲能够"承受"的伤害区间，韧性越高，完全减伤与部分减伤的范围越大。
- 对于高额伤害，护甲不再有原版的保底减伤，玩家需要依靠走位或换更好的护甲。

耐久损耗

- 护甲损耗不再固定为"伤害的 1/4"，而是由伤害与盔甲韧性共同决定。
- 盔甲韧性高的护甲会更慢磨损，盔甲韧性低的护甲则更快磨损。

护甲耐久度

- 每件护甲的耐久由材料本身的属性决定，不同部位有不同的耐久系数，轻甲/中甲/重甲再叠加独立的耐久调整。
- 材料耐久遵循现实硬度排序：皮革较脆，钻石较坚固，铁居中；每种材料都有清晰可感知的耐久定位，不再只是"高级材料全面更强"。

修理系统

- 铁砧修理重做：修理费用=消耗的材料数量，不再累积经验惩罚，也不再有"过于昂贵"上限——装备可以无限次修理，永无上限。
- 单个材料的修复量随材料与护甲类型变化：耐久基数越高的材料，单个材料修复量越大；轻甲修复效率高、重甲修复效率低。
- 结果：修理变成长期维护的一部分，而非前期划算、后期弃置的选项。

质量系统

- 每件装备都有质量：由材料系数、护甲类型与护甲值共同决定。材质越厚重，质量越大（例如金质装备明显偏重）。
- 穿戴装备会提升负重比例，负重越高：
  - 击退抗性越高（更难被击退）；
  - 移动速度越慢。
- 特殊材料有独特定位：如下界合金兼具重甲的保护力与较轻的佩戴负担。
- 在多模组环境中，玩家的质量会随着最大生命值的增加而增加。

摔落伤害

- 摔落伤害由玩家自身质量 + 穿戴装备质量、重力与下落高度共同决定——装备越重，摔落越疼。
- 跳跃提升改为按百分比拓宽安全高度：如果你的安全距离较高，每级跳跃提升会显著提升免伤高度，而非原版的微量提升。
- 无护甲且最大生命值为20时与原版体验一致；负重越重，玩家越需要留意垂直落差。

耐久附魔修改

- 耐久附魔由"概率性免损"改为确定性减免：损耗按公式固定衰减，每级耐久稳定提升减免幅度。
- 低伤害攻击在高级耐久下可能完全不损耗耐久！

反伤系统与荆棘附魔

- 新增反伤机制：护甲损失耐久时对攻击来源造成反伤，反伤量与本次损耗的耐久成正比。
- 荆棘附魔并入该系统：由原版的"概率反伤"改为必定反伤，附魔等级直接提升反伤比例，不再依赖随机性。
- 低伤害攻击若不足以让护甲掉耐久，则不会触发反伤。
- 支持诅咒类护甲设定：特定材料可将反伤改为必定反弹给穿戴者自己。

负属性支持

- 护甲韧性与击退抗性的取值下限向负数开放：
  - 负韧性被允许，但负韧性护甲会显著磨损更快且完全失去低伤区。
  - 负击退抗性意味着被击退得更远，并已默认启用。
- 为附属模组与整合包作者提供了负属性的设计空间。

工具耐久公式（默认关闭）

- 可选启用：工具耐久由对应护甲材料的耐久基数推导，护甲与同材料的工具共享同一个耐久基准。
- 调整材料耐久基数时，护甲与工具同步联动，保持体系一致。
- 默认关闭，不影响原版工具体验；启用后第三方材料/工具亦可纳入。
- 原版环境下，启用后耐久较原版变化不大。

---

New Armor System

This mod completely overhauls the armor system: damage reduction is no longer a fixed percentage, but takes effect in segments based on the size of the damage taken. Armor wears down in combat, but you can repair it an unlimited number of times. Wearing different armor affects your movement speed and fall damage — every piece of armor has its own physical properties.
This mod does not add new armor, new enchantments, or new items. Add-on mods may be developed in the future, which will add concrete equipment on top of this foundation.
Design philosophy: give every piece of armor a clear, perceivable role, making armor choice a meaningful strategic decision rather than simply comparing reduction numbers.

Armor Damage Reduction

- Reduction is no longer a simple percentage, but a three-zone rule:
  - Low-damage zone: when damage is below the armor's capacity, the armor reduces it by 80%;
  - Mid-damage zone: reduction decays linearly as damage increases;
  - High-damage zone: armor completely fails, 0% reduction.
- The role of armor toughness is strengthened: toughness and armor value together determine the damage range the armor can "absorb". The higher the toughness, the larger the full-reduction and partial-reduction ranges.
- Against high damage, armor no longer has vanilla's guaranteed baseline reduction — players must rely on positioning or better armor.

Durability Loss

- Durability loss is no longer fixed at "1/4 of damage", but is determined by both damage and armor toughness.
- Armor with high toughness wears down more slowly; armor with low toughness wears down faster.

Armor Durability

- Each piece of armor's durability is determined by the material's intrinsic properties. Different slots have different durability factors, with light/medium/heavy armor types applying independent durability adjustments on top.
- Material durability follows real-world hardness ordering: leather is fragile, diamond is strong, iron sits in between. Every material has a clear, perceivable durability role — no longer just "higher-tier materials are better at everything".

Repair System

- Anvil repair rework: repair cost = number of materials consumed. Experience penalties no longer accumulate, and there is no "Too Expensive" cap — equipment can be repaired infinitely, forever.
- The amount restored per material varies by material and armor type: materials with higher base durability restore more per material; light armor repairs efficiently, while heavy armor repairs slowly.
- Result: repair becomes part of long-term maintenance rather than an option that is worthwhile early and abandoned later.

Mass System

- Every piece of equipment has a mass, determined by material coefficient, armor type, and armor value. The heavier the material, the greater the mass (e.g., gold equipment is notably heavy).
- Wearing equipment raises your load ratio. The higher the load:
  - Knockback resistance increases (harder to be knocked back);
  - Movement speed decreases.
- Special materials have unique roles: e.g., netherite combines heavy armor's protection with a lighter burden.
- In multi-mod environments, a player's mass increases as max health increases.

Fall Damage

- Fall damage is determined by the player's own mass + the mass of worn equipment, gravity, and fall height — the heavier the equipment, the harder the fall.
- Jump Boost now widens the safe height by a percentage: if your safe distance is high, each level of Jump Boost significantly increases the no-damage height, rather than vanilla's tiny boost.
- With no armor and 20 max health, the experience matches vanilla; the heavier your load, the more you must watch for vertical drops.

Unbreaking Modification

- Unbreaking is changed from "probabilistic damage avoidance" to deterministic reduction: loss is reduced by a fixed formula, and each level of Unbreaking steadily increases the reduction.
- Low-damage attacks may cause zero durability loss under high Unbreaking levels!

Reflection System & Thorns Enchantment

- New reflection mechanic: when armor loses durability, it deals reflection damage to the attacker, proportional to the durability lost this hit.
- Thorns is merged into this system: from vanilla's "chance-based reflection" to guaranteed reflection, with the enchantment level directly increasing the reflection ratio — no more reliance on randomness.
- If a low-damage attack is not enough to reduce the armor's durability, no reflection is triggered.
- Supports cursed armor setups: certain materials can turn reflection into a guaranteed bounce-back to the wearer instead.

Negative Attribute Support

- The lower bounds of armor toughness and knockback resistance are opened to negative values:
  - Negative toughness is allowed, but negative-toughness armor wears down significantly faster and completely loses the low-damage zone.
  - Negative knockback resistance means you get knocked back further, and this is enabled by default.
- Provides negative-attribute design space for add-on mod and modpack authors.

Tool Durability Formula (Disabled by Default)

- Optional: tool durability is derived from the corresponding armor material's base durability — armor and tools of the same material share the same durability baseline.
- When you adjust a material's base durability, armor and tools update in sync, keeping the system consistent.
- Disabled by default; vanilla tool experience is unaffected. When enabled, third-party materials/tools can also be included.
- In a vanilla environment, durability changes little compared to vanilla after enabling.
