# 【耕食记】(Cuisine & Farming) 技术架构设计文档

## 1. 项目概述 (Project Overview)
**耕食记** 是一个深度整合农业种植与烹饪制作的沉浸式经营插件。它旨在通过引入“土地肥力”、“温室环境”、“实体烹饪”和“共享飨宴”等机制，将原本枯燥的挂机农业转化为需要策略经营的循环玩法。

本项目将作为独立插件开发，同时与 **EarthSpirit (地灵系统)** 和 **BiomeGifts (地域馈赠)** 深度联动。

---

## 2. 核心模块技术分析 (Technical Analysis)

### 2.1 土地肥力系统 (Soil Fertility)
**核心需求**: 每块耕地拥有独立的肥力值，动态平衡消耗与自然恢复，提供直观的视觉反馈。
**技术选型**: **PDC (PersistentDataContainer)**。数据直接绑定在区块 (Chunk) 上，性能最优且随地图保存。

*   **机制**:
    *   **消耗**: 作物生长/收割时扣除肥力。
    *   **恢复**: 
        *   **自然恢复**: 耕地在湿润状态下持续缓慢增加肥力（无需强制休耕）。
        *   **动态平衡**: 高肥力时恢复慢，低肥力时恢复快（防止极端透支）。
        *   **堆肥**: 玩家主动使用肥料（大幅恢复）。
    *   **影响**: 肥力值直接决定作物的生长速度和**品质星级**。

*   **视觉反馈 (Visuals)**:
    *   **贴图替换**: 利用 **ItemsAdder/Oraxen** 替换耕地模型。
        *   **肥沃土 (High)**: 深黑、湿润光泽。
        *   **普通土 (Mid)**: 原版材质。
        *   **贫瘠土 (Low)**: 发白、干裂、沙化。
    *   **粒子**: 肥力耗尽冒灰烟，施肥成功飘绿星。
    *   **农夫单片镜**: 佩戴特定道具时，ActionBar 显示精准数值 (🌱 肥力: 120/100)。

### 2.2 种植与选种体系 (Farming & Genetics) - [重构版]
**核心目标**: 抛弃原有的简单数值叠加，构建基于“孟德尔遗传学”与“表观遗传学”的深度农业系统。将“获得种子”转化为“维持种质资源”的动态经营玩法。

#### 2.2.1 核心架构：基因型与表型 (Genotype vs. Phenotype)
**现状**: 种子直接存储最终属性 (e.g., `Speed: 1.5x`)，逻辑简单但缺乏深度。
**改动**: 引入**双层数据结构**。

1.  **基因型 (Genotype - 存储层)**:
    *   **定义**: 作物真实的遗传信息，存储在 `PDC` (区块) 或 `ItemMeta` (种子物品) 中。
    *   **结构**: 采用**双等位基因 (Diploid)** 系统。每个性状由一对基因控制。
        *   *示例*: `Growth_Gene: [Dominant, Recessive]` (杂合子)。
    *   **基因等级**:
        *   `Wild (W)`: 野生型，基础值 (Value=1)。
        *   **`Domestic (D)`**: 驯化型，高产但脆弱 (Value=2)。
        *   **`Special (S)`**: 突变型，拥有特殊能力 (Value=4)。

2.  **表型 (Phenotype - 表现层)**:
    *   **定义**: 作物在当前环境下实际表现出的数值。
    *   **计算时机**: 作物生长(BlockGrowEvent)、收割(BlockBreakEvent)、鉴定(Interact)时实时计算。
    *   **公式**: `Phenotype = BaseValue * GeneExpression * EnvironmentalMultiplier`。

#### 2.2.2 环境表观遗传 (Environmental Epigenetics)
**现状**: BiomeGifts 仅提供静态的 `Rich/Poor` 修正，与作物基因无直接交互。
**改动**: 建立 **"Environment Context (环境上下文)"** 交换协议。

1.  **数据流向**:
    *   **Input**: 
        *   `BiomeGifts`: 提供群系特征 (Rich/Poor, Swamp/Desert)。
        *   `RealisticSeasons`: 提供实时气温 (Temp)、湿度 (Rain)。
    *   **Process (CuisineFarming)**: 
        *   基因判断环境是否匹配 (e.g., `[耐寒]` 基因检测 `Temp < 5°C`)。
        *   计算**适应性系数 (Adaptability Score)**。
    *   **Output**: 
        *   返回给 `BiomeGifts` 一个最终的 `EffectiveLuck` 用于计算掉落。
        *   返回给 `CropTask` 一个 `GrowthRate` 用于计算生长。

2.  **基因-环境互作逻辑 (GxE Interaction)**:
    *   **适应性 (Adaptation)**: 
        *   `[耐旱]` 基因在 `Desert` 群系：表现 = 150% (优势表达)。
        *   `[耐旱]` 基因在 `Jungle` 群系：表现 = 80% (根系腐烂)。
    *   **胁迫 (Stress)**:
        *   `[巨大化]` 基因需要 `Rich` 土壤支持。若在 `Poor` 土壤种植，表现值强制降为 50% (营养不良)。

#### 2.2.3 杂交与繁育循环 (Hybridization & Breeding Loop)
**现状**: 简单的父本数值平均 + 随机突变。
**改动**: 引入 **"杂种优势 (Heterosis)"** 与 **"性状分离 (Segregation)"**。

1.  **遗传算法 (The Punnett Square)**:
    *   **配子生成**: 父本 `[A, B]` 随机贡献 `A` 或 `B`。
    *   **合子形成**: `Parent1_Gamete + Parent2_Gamete -> Offspring_Genotype`。

2.  **核心机制：杂种优势 (Hybrid Vigor)**:
    *   **设定**: 杂合子 `[S, D]` 的表型强度 > 纯合子 `[S, S]`。
    *   *数值示例*:
        *   `[S, S]` (纯合): 产量 120% (稳定，可留种)。
        *   `[S, D]` (杂合): 产量 160% (极强，**不可稳定留种**)。
    *   **玩法影响**: 
        *   玩家不能只通过一次杂交获得“毕业种子”。
        *   必须维护 **亲本田 (Parent Fields)** 种植纯合子 `[S, S]` 和 `[D, D]`。
        *   每年春天进行 **制种 (Seed Production)**，杂交产生 F1 代 `[S, D]` 用于当季生产。

3.  **退化机制 (Degeneration)**:
    *   若玩家强行种植 F1 代产出的种子 (F2)，将发生性状分离：
        *   25% `[S, S]` (良)
        *   50% `[S, D]` (优)
        *   25% `[D, D]` (中)
    *   田地里作物参差不齐，导致批量收割困难，整体效率下降。

#### 2.2.4 跨插件调用链路 (Integration Pipeline)
1.  **生长阶段 (Growth)**:
    *   `BlockGrowEvent` -> `CuisineFarming` 读取 PDC 基因 -> 获取 `RealisticSeasons` 温度 -> 计算生长速度 -> 决定是否取消/加速生长。

2.  **掉落阶段 (Drops - The "Second Knife")**:
    *   `BlockBreakEvent` -> `BiomeGifts` 介入。
    *   `BiomeGifts` 构建 `EnvironmentContext` (Rich/Poor, BiomeType)。
    *   `BiomeGifts` 调用 `CuisineFarming.getEffectiveYield(Block, Context)`。
    *   `CuisineFarming` 根据基因+环境计算最终倍率 (e.g., 1.5x)。
    *   `BiomeGifts` 结合自身的 BaseChance，执行最终掉落逻辑。

#### 2.2.5 物品与 UI (Items & UI)
*   **鉴定镜 (Magnifying Glass)**: 
    *   左键点击作物 -> 聊天栏显示详细基因图谱 (e.g., `生长: [S, D] (强盛)`).
*   **授粉笔 (Pollen Brush)**: 
    *   右键作物 A (吸取花粉) -> 右键作物 B (授粉) -> 消耗耐久，B 结出的种子变为杂交种。
*   **种子袋 (Seed Bag)**:
    *   用于批量存储和筛选基因，避免背包混乱。

### 2.3 厨房与烹饪 (Kitchen & Cooking)
**核心需求**: 拆解烹饪流程，引入 Tier (复杂度) 与 Quality (品质) 双维度评价，增加 QTE 操作感。

#### A. 双维度评价体系
*   **菜谱复杂度 (Tier)**: 决定上限。
    *   T1 (清炒土豆丝): 上限 3星。
    *   T3 (佛跳墙): 上限 5星。
*   **成品品质 (Star Level)**: 决定实际效果。
    *   公式: `FinalStars = (食材平均星级 * 50%) + (QTE评分 * 50%)`。
    *   这意味着用垃圾食材或 QTE 失败，做出的 T3 菜肴也可能是 1星“黑暗料理”。

#### B. 厨房工作台矩阵
1.  **切菜板 (Cutting Board)**: 
    *   放置食材 -> 刀具点击 -> 播放音效 -> 产出半成品。
2.  **研磨钵/酿造坛 (Mortar / Jar)**: 
    *   处理香料/腌制食品，增加时间维度。
3.  **厨神铁锅 (Cooking Pot) - 核心交互**:
    *   **QTE 小游戏: 火候控制 (Temperature Control)**
        *   **UI**: BossBar 显示温度条 (蓝-冷 -> 绿-完美 -> 红-焦)。
        *   **机制**: 烹饪开始后温度自动上升。
        *   **操作**: 右键搅拌(降温)，加柴火(升温)。
        *   **判定**: 倒计时结束时，温度条停留在绿色区间为大成功；红色为失败(黑暗料理)；蓝色为小成功。

### 2.4 味觉图谱 (Flavor Profile)
*   **标签体系**: 【甜】、【辣】、【鲜】、【温】。
*   **作用**: 连接食材与最终料理，未来可扩展至 NPC/地灵喜好。

---

## 3. 开发优先级与路线图 (Roadmap)

### Phase 1: 基础架构 (Infrastructure)
1.  **项目搭建**: 引入 ItemsAdder, RealisticSeasons, EarthSpirit, BiomeGifts 依赖。 [x]
2.  **数据层**: 完善 `FertilityManager`，实现基于 PDC 的肥力逻辑与自然恢复算法。 [x]
3.  **物品库**: 定义基础农具、肥料、自定义耕地贴图 (High/Mid/Low)。 [x] (基础农具/肥料已完成，贴图待接入)

### Phase 2: 生产端 (Production)
1.  **种植逻辑**: 监听 `BlockGrowEvent`，接入肥力、季节、温室温度判定。 [x] (肥力判定已完成)
2.  **视觉反馈**: 实现耕地材质随肥力动态切换逻辑。 [-] (农夫单片镜已完成，材质切换待办)
3.  **选种与杂交**: 开发种子 NBT 基因存储、鉴定机 UI、授粉架逻辑。 [ ]

### Phase 3: 加工端 (Processing)
1.  **工作台**: 实现切菜板与研磨钵的交互逻辑。 [ ]
2.  **烹饪核心**: 开发厨神铁锅实体交互，实现 BossBar 火候控制 QTE。 [ ]
3.  **评价系统**: 实现基于“食材+QTE”的双维度成品星级计算。 [ ]

---

## 4. 技术栈确认 (Tech Stack Decisions)

*   **资源包/模型**: **ItemsAdder** (必须，用于自定义耕地贴图、厨具模型、菜肴模型)。
*   **季节系统**: **RealisticSeasons** (API Hook，获取环境温度)。
*   **数据存储**: **PDC (PersistentDataContainer)** (用于区块肥力数据)。
*   **核心依赖**: EarthSpirit, BiomeGifts, ProtocolLib.

---

**文档维护人**: Trae
**更新日期**: 2025-12-20
