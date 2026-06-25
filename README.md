# OpenClaw Wargame · AI 自主战争仿真系统

> 一套基于 Java 17 的**军事战争实时分析与无人化决策**软件。
> 战场感知 → 态势分析 → AI 决策 → 武器调度 → 自动反击，全程无人工干预。
> 现已支持**强化学习**、**实时 Web 可视化大屏**和**战术顾问 Buff 增益系统**。

[![Java 17](https://img.shields.io/badge/Java-17-blue)]()
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-orange)]()
[![Tests](https://img.shields.io/badge/Tests-90%20passed-green)]()
[![Modules](https://img.shields.io/badge/Modules-10-purple)]()

---

## 🎯 项目愿景

**军事战争实时分析** + **战争无人化** + **自动决策与反击** + **武器设备自我调度**

本项目以仿真形式实现了一个完整的 OODA（Observe → Orient → Decide → Act）作战循环，
两支自主军队在没有人工干预的情况下相互对抗。

**V1.2 新增**：战术顾问系统（TacticalAdvisor）持续监控战场，给指挥官提供**实时建议**
（攻击/防御/侧翼/后撤/补给）和**Buff 增益分配**（火力+/装甲+/机动+/探测+），让 AI 不只做决策，
还能主动**增强战场优势**。

**亮点能力**：
- 🧠 **三套 AI 大脑可切换**：规则引擎 / 蒙特卡洛树搜索 / **Q-Learning 强化学习**
- 📊 **实时 Web 大屏**：浏览器打开 `http://localhost:18080/` 看战场态势
- 🎖️ **战术顾问 Buff 增益**：AI 实时分析"火力/人员/机动/探测/协同"五维优势，主动给单位加 buff 增强
- ⚡ **毫秒级 OODA 循环**：每 5 秒推进一个 tick，事件总线支持多订阅者
- 🎯 **自动武器调度**：火控计算机 + ROE 交战规则 + 命中概率结算

---

## 🏗️ 系统架构（V1.1）

```
                              ┌─────────────────────┐
                              │   openclaw-wargame  │
                              │     (root pom)      │
                              └──────────┬──────────┘
                                         │
        ┌────────┬────────┬─────────────┼─────────────┬────────────┬─────────────┬────────┐
        ▼        ▼        ▼             ▼             ▼            ▼             ▼        ▼
      ┌──────┐┌────────┐┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌────────┐
      │ core ││realtime││analysis │ │   ai    │ │ weapon  │ │   sim   │ │  rl     │ │  web   │
      └──────┘└────────┘└─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └────────┘
      战场模型  事件总线  态势分析   决策引擎     火控/调度   仿真引擎    强化学习    HTTP+大屏
                                                      ▲                     ▲
                                                      │                     │
                                                      └──── autonomy ──────┘
                                                            OODA 循环
```

### 模块职责

| 模块 | 包 | 关键类 | 职责 |
|------|------|---------|------|
| **core** | `core` | `Unit`, `Weapon`, `TerrainMap`, `BattleState`, `Team` | 战场领域模型 |
| **realtime** | `realtime` | `BattleEventBus`, `BattleClock`, `BattleEvent` | 实时事件流（无锁环形缓冲） |
| **analysis** | `analysis` | `SituationalAnalysis`, `ThreatAssessment`, `BattleAdvantage`, `TacticalAdvisor` | 战场态势分析、威胁评估、**战场优势量化、战术顾问建议** |
| **ai** | `ai` | `RuleEngine`, `MonteCarloTreeSearch`, `Action`, `DecisionPlan` | AI 决策引擎（规则 + MCTS） |
| **weapon** | `weapon` | `FireControlComputer`, `WeaponScheduler`, `RulesOfEngagement` | 武器调度、火控计算 |
| **simulation** | `simulation` | `Simulator` | 确定性仿真引擎（支持 MCTS 回放） |
| **autonomy** | `autonomy` | `AutonomyLoop`, `AutonomousCommander`, `BattleRunner`, `RLAgent` | 无人化作战循环（OODA） |
| **rl** | `rl` | `QLearner`, `StateFeatures`, `Strategy`, `StrategyInterpreter` | Q-Learning 强化学习 |
| **web** | `web` | `WargameServer`, `BattleStateHolder` | HTTP 服务 + 静态资源 + REST API |
| **demo** | `demo` | `BattleDemo` | 端到端命令行/Web/RL 演示 |

---

## 🚀 快速开始

### 环境要求

- Java 17+
- Maven 3.8+

### 编译 + 测试

```bash
mvn clean test          # 10 模块 70 测试全部通过
mvn package -DskipTests # 产出 demo fat jar
```

### 三种运行模式

#### 1️⃣ 命令行模式（默认规则引擎）

```bash
java -jar openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar 42 25
```

#### 2️⃣ Web 可视化模式（推荐）

```bash
java -jar openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar 42 60 --web --port 18080
```

然后用浏览器打开 **http://localhost:18080/** 看实时战场态势：
- 地图上每个点是一个单位（蓝/红/灰）
- 黄色光环 = 正在交火
- 右侧面板显示火力优势、Top 威胁、事件流
- 500ms 自动刷新

#### 3️⃣ RL 强化学习训练模式

```bash
# 蓝方用 RL 训练 10 个 episode
java -jar openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar 42 30 --rl --episodes 10

# 混合模式：RL 选 Strategy + 规则补足细节
java -jar openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar 42 30 --hybrid --episodes 5
```

输出示例：
```
=== Episode 1/5 ===
[BLUE tick=0] mode=RL command: 9 actions | RL[aggressive] ε=0.300 states=1
[BLUE tick=6] mode=RL command: 6 actions | RL[defensive]  ε=0.300 states=2
[BLUE tick=18] mode=RL command: 3 actions | RL[retreat]    ε=0.300 states=4
  Episode 1 result: RED (blue=0 red=4)
    blue ε=0.2985 states=4 updates=14
=== Episode 5/5 ===
  Episode 5 result: DRAW (blue=5 red=1)
    blue ε=0.2899 states=8 updates=58
===============================================================
  RL Training Report
===============================================================
  Episodes: 5
  Blue wins: 1 (20.0%)
  Red wins:  2 (40.0%)
  Draws:     2 (40.0%)
===============================================================
```

---

## 🧠 核心特性

### 0. **战术顾问与战场优势增强** (V1.2)

`TacticalAdvisor` 每 tick 输出：
- **`BattleAdvantage`** —— 5 维量化（firepower / manpower / detection / mobility / cohesion + overall [0-1]）
- **`Advice`** —— 9 种战术建议（ATTACK / DEFEND / FLANK / RETREAT / RECON / RESUPPLY / COORDINATE_FIRE / EMERGENCY_RETREAT / STAND_DOWN）
- **`BuffAssignment`** —— 7 种 Buff 增益（FIREPOWER +30%、ARMOR +50%、SPEED +40%、DETECTION +50%、STEALTH、SUPPRESSION、COORDINATION）

Buff 由 Simulator 每 tick 衰减，叠加生效。Web 大屏上单位有 ✨ 标记表示有活跃 Buff。

### 1. **三套 AI 大脑**

| 模式 | 实现 | 适用场景 |
|------|------|----------|
| **RULE** | 5 条规则 + 优先级合并 | 可解释、生产环境 |
| **MCTS** | UCB1 + 100 次迭代 + 8 步推演 | 战术推演、研究 |
| **RL** | Q-Learning + ε-greedy + 5 策略 | 自主学习、博弈 |

通过 `AutonomousCommander.setMode()` 切换，无需重启。

### 2. **Q-Learning 强化学习（V1.1 新增）**

- **状态特征**（4 × 3 × 3 × 3 × 2 = 216 种状态）：
  - 火力优势档（劣势/均势/优势/压倒）
  - 己方存活比例
  - 最近敌方距离
  - 威胁数
  - 是否存在残血单位
- **动作空间**（5 个 Strategy）：
  - AGGRESSIVE（集火强攻）
  - DEFENSIVE（收缩防御）
  - FLANKING（侧翼包抄）
  - RETREAT（全面后撤）
  - RECON_FOCUS（侦察优先）
- **奖励函数**：杀死敌人 +10，损失己方 -15，火力优势奖励
- **训练过程**：ε 从 0.3 衰减到 0.05（每 episode ×0.995）

### 3. **OODA 作战循环**

```
        Observe                Orient               Decide              Act
           │                     │                    │                  │
           ▼                     ▼                    ▼                  ▼
   战场事件总线 ───►  态势分析（威胁/弱点） ───►  规则+MCTS+RL ───►  武器调度
                                                              (act → bus.publish)
```

每方每个 tick 完整跑一遍循环，全自动决策。

### 4. **实时 Web 可视化大屏（V1.1 新增）**

零依赖（仅用 JDK 内置 `com.sun.net.httpserver.HttpServer`）：
- HTML5 Canvas 绘制战场地图（蓝/红/灰/交火光环）
- 右侧面板显示：火力优势 / Top 威胁 / 最近事件
- REST API：`/api/snapshot`, `/api/analysis/{blue|red}`, `/api/events`
- 500ms 轮询，实时刷新
- 可视化页面响应式设计，桌面/平板都能看

### 5. **完整武器模型**

- 12 种武器 × 10 种单位 × 7 种地形
- 交战规则（ROE）：`HOLD` / `TIGHT` / `FREE` / `FREE_ALL`
- 火控计算机：贪心配对 + 命中率计算 + 饱和攻击
- 9 类实时事件：`DETECTION` / `WEAPON_FIRED` / `HIT` / `KILL` / ...

---

## 📊 测试覆盖

| 模块 | 测试数 | 备注 |
|------|--------|------|
| core | **24** | Position, TerrainMap, Unit, Weapon |
| realtime | **4** | BattleEventBus |
| analysis | **4** | SituationalAnalysis |
| ai | **4** | RuleEngine |
| weapon | **4** | FireControlComputer |
| simulation | **0** | （集成测试由 autonomy 覆盖） |
| autonomy | **6** | BattleRunner + RLAgent |
| rl | **18** | QLearner + StateFeatures + StrategyInterpreter |
| web | **6** | WargameServer HTTP API |
| demo | **0** | （集成由 demo 运行验证） |
| **总计** | **70** | **全过 ✅** |

---

## 🎮 调参 / 扩展

### 自定义规则

```java
RuleEngine engine = DefaultRules.createDefault()
    .register(new MyCustomRule());

public class MyCustomRule implements Rule {
    public String name() { return "MyCustom"; }
    public List<Action> evaluate(BattleState s, SituationalAnalysis sa) {
        // 你的策略逻辑
        return actions;
    }
}
```

### 自定义交战规则（ROE）

```java
RulesOfEngagement roe = new RulesOfEngagement()
    .setMode(RulesOfEngagement.Mode.WEAPONS_TIGHT)
    .setMinEngagementDistance(500);
FireControlComputer fcc = new FireControlComputer(roe);
```

### 自定义 RL 奖励

修改 `StrategyInterpreter.reward()`：
```java
public static double reward(BattleState prev, BattleState curr, Team team) {
    double r = 0;
    r += 10.0 * (prev.aliveCount(enemy) - curr.aliveCount(enemy));  // 击杀
    r -= 15.0 * (prev.aliveCount(team) - curr.aliveCount(team));    // 损失
    // ... 加你自己的奖励项
    return r;
}
```

### 自定义军队

```java
Unit armor = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(1000, 1000));
armor.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
armor.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 500));
```

---

## 📜 路线图

- [x] **V1.0** — 8 模块核心骨架 + 端到端 Demo
- [x] **V1.1** — RL 强化学习 + Web 可视化大屏 ← 当前
- [ ] **V1.2** — 集群协作（多 commander 协同决策）
- [ ] **V1.3** — 接入真实地图（OpenStreetMap / 高程数据）
- [ ] **V1.4** — 接入真实武器平台 API（火控系统/无人机）
- [ ] **V2.0** — 分布式仿真（多个仿真节点协同对抗）

---

## 📝 License

Apache 2.0

---

## 🙏 致谢

- 继承自 [OpenClaw Enterprise](https://github.com/liugl951127/openClaw) 工程实践经验
- 战场模型参考兵棋推演设计原则
- AI 部分采用经典 OODA 决策论 + MCTS + Q-Learning
