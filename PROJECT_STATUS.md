# OpenClaw Wargame — Project Status (v1.2.0)

> 实时战争战场分析 + 战争无人化 + 自动决策与反击 + AI 战术顾问与战场优势增强

**仓库**：https://github.com/liugl951127/aiWar
**最新 Release**：https://github.com/liugl951127/aiWar/releases/tag/v1.2.0
**License**：Apache 2.0

---

## 🎯 愿景达成度

| 需求 | 状态 | 实现位置 |
|------|------|----------|
| 军事战争实时分析 | ✅ | `SituationalAnalysis`, `BattleAdvantage`, `BattleEventBus` |
| 战争无人化 | ✅ | `AutonomyLoop` OODA 循环 + `AutonomousCommander` |
| 自动决策与反击 | ✅ | `RuleEngine` + `MCTS` + `RLAgent` 三套 AI |
| 武器设备自我调度 | ✅ | `FireControlComputer` + `WeaponScheduler` + ROE |
| AI 实时辅助作战 | ✅ | `TacticalAdvisor` 实时建议 |
| 强化战场优势 | ✅ | `Buff` 系统（7 种增益） |

---

## 📊 项目数据

```
总代码量    : 5,850 行 Java
Java 文件   : 63 (44 主代码 + 17 测试 + 2 资源)
Maven 模块  : 10
测试用例    : 90 (全部通过)
测试覆盖    : 7 个模块有独立测试
Git 提交    : 3 commits
GitHub Tags : v1.1.0, v1.2.0
```

### 按模块分布

| 模块 | 主代码 | 测试 | 关键能力 |
|------|--------|------|----------|
| `core` | 922 行 / 11 文件 | 32 测试 | Unit/Weapon/Terrain/Buff/Position 战场领域模型 |
| `realtime` | 356 行 / 7 文件 | 4 测试 | 无锁事件总线 (CAS ring buffer, BLOCK/DROP) |
| `analysis` | 549 行 / 4 文件 | 16 测试 | 态势分析 + 威胁评估 + **5维优势 + 战术顾问** |
| `ai` | 612 行 / 7 文件 | 4 测试 | 规则引擎 (5 rules) + 蒙特卡洛树搜索 |
| `weapon` | 245 行 / 4 文件 | 4 测试 | 火控计算机 + 交战规则 + 武器调度 |
| `simulation` | 140 行 / 1 文件 | 0 测试 | 确定性仿真引擎 (MCTS 回放用) |
| `rl` | 495 行 / 4 文件 | 18 测试 | Q-Learning + 216 状态 + 5 策略 |
| `autonomy` | 474 行 / 4 文件 | 6 测试 | OODA 循环 + RL 智能体 + 战斗编排 |
| `web` | 415 行 / 3 文件 | 6 测试 | HTTP 服务 + HTML5 大屏 + 静态资源 |
| `demo` | 268 行 / 1 文件 | (集成) | 端到端 CLI (--web / --rl / --hybrid) |
| **总计** | **4476 行 / 47 文件** | **90 测试** | |

---

## 🏗️ 架构总览

```
                              ┌─────────────────────┐
                              │   openclaw-wargame  │
                              │     (root pom)      │
                              └──────────┬──────────┘
                                         │
   ┌─────────────┬─────────────┬──────────┼──────────┬─────────────┬────────────┬────────┐
   ▼             ▼             ▼          ▼          ▼             ▼            ▼        ▼
┌──────┐    ┌─────────┐  ┌──────────┐ ┌────────┐ ┌────────┐   ┌─────────┐  ┌─────────┐ ┌────────┐
│ core │◄───│realtime │  │analysis  │ │  ai    │ │ weapon │   │   sim   │  │   rl    │ │  web   │
│Unit  │    │EventBus │  │SitAnaly  │ │Rules   │ │FCC+ROE │   │Simulate │  │Q-Learn  │ │HTTP+UI │
│Weapon│    │Clock    │  │Threat    │ │MCTS    │ │Schedule│   │ShallowCpy│ │State    │ │SSE poll│
│Buff  │    │9 kinds  │  │Advantage │ │Action  │ │        │   │         │  │Strategy │ │RestAPI │
│Map   │    │events   │  │Advisor   │ │        │ │        │   │         │  │         │ │        │
└──┬───┘    └─────────┘  └────┬─────┘ └────┬───┘ └────┬───┘   └────┬────┘  └────┬────┘ └───┬────┘
   │                         │             │          │             │             │         │
   │                         │             │          │             │             │         │
   └─────────────────────────┴─────────────┴──────────┴─────────────┴─────────────┴─────────┘
                                              │
                                              ▼
                                  ┌────────────────────┐
                                  │ openclaw-wargame-  │
                                  │    autonomy        │
                                  │ OODA Loop          │
                                  │ RL Agent           │
                                  │ BattleRunner       │
                                  └─────────┬──────────┘
                                            │
                                            ▼
                                  ┌────────────────────┐
                                  │ openclaw-wargame-  │
                                  │       demo         │
                                  │ BattleDemo (CLI)   │
                                  │ +4 modes (RULE/RL/ │
                                  │  HYBRID/WEB)       │
                                  └────────────────────┘
```

### OODA 作战循环（每阵营每 tick）

```
        Observe                Orient               Decide              Act
           │                     │                    │                  │
           ▼                     ▼                    ▼                  ▼
   BattleEventBus ───►  TacticalAdvisor  ───►  Rule+MCTS+RL  ───►  Commander.apply
   (最近事件流)         + SituationalAnalysis    (3 套 AI 大脑)    + FireControl
                        + BattleAdvantage                         + WeaponScheduler
                          (5 维战场优势)                            (实际开火)
                                  │
                                  ▼
                         Buff Assignments
                        (自动应用 FIREPOWER/
                         ARMOR/SPEED/DETECTION)
```

---

## 🧠 AI 决策大脑（3 套可切换）

| AI 大脑 | 算法 | 状态空间 | 动作空间 | 训练 |
|---------|------|----------|----------|------|
| **RuleEngine** | 5 条规则 + 优先级 | 连续 | Action 子集 | 无 |
| **MCTS** | UCB1 + 100 迭代 | 连续 | 随机 rollout | 无 |
| **QLearner** | ε-greedy Q-Learning | 216 离散 | 5 Strategy | ✓ |

切换方式：`AutonomousCommander.setMode(DecisionMode.RULE/RL/HYBRID)`

---

## 🎖️ TacticalAdvisor —— 实时战术顾问

### 输出三件套

1. **BattleAdvantage**（5 维量化）
   - `firepower` (权重 0.35)
   - `manpower` (权重 0.30) — 综合考虑存活数 + 平均血量
   - `detection` (权重 0.15)
   - `mobility` (权重 0.10)
   - `cohesion` (权重 0.10)
   - `overall` (0~1 综合分)

2. **Advice**（9 种战术建议）
   - `ATTACK`, `DEFEND`, `FLANK`, `RETREAT`, `RECON`
   - `RESUPPLY`, `COORDINATE_FIRE`
   - `EMERGENCY_RETREAT`（紧急）
   - `STAND_DOWN`（默认）

3. **BuffAssignment**（7 种 Buff 增益）
   - `FIREPOWER` +30% / `ARMOR` +50% / `SPEED` +40% / `DETECTION` +50%
   - `STEALTH`（伪装）/ `SUPPRESSION`（压制）/ `COORDINATION`（协同）

### 决策逻辑

```
火力优势 > 70%      → COORDINATE_FIRE (priority 80)
综合优势 > 60%       → ATTACK        (priority 70)
机动优势大火力弱     → FLANK         (priority 65)
综合劣势 < 40%      → DEFEND        (priority 60)
弹药 < 20%          → RESUPPLY      (priority 55)
己方损失 > 70%      → EMERGENCY_RETREAT (priority 100)
```

---

## 🌐 Web 可视化大屏

**零依赖**（仅用 JDK 内置 `com.sun.net.httpserver.HttpServer`）

### 路由

| 路径 | 用途 |
|------|------|
| `GET /` | HTML5 Dashboard |
| `GET /static/style.css` | 样式 |
| `GET /static/dashboard.js` | 前端逻辑 |
| `GET /api/state` | 完整 BattleState JSON |
| `GET /api/snapshot` | 轻量快照（每 500ms 轮询） |
| `GET /api/analysis/blue` | 蓝方态势分析 |
| `GET /api/analysis/red` | 红方态势分析 |
| `GET /api/advisory/blue` | 蓝方战术顾问报告 |
| `GET /api/advisory/red` | 红方战术顾问报告 |
| `GET /api/events` | 最近 50 条事件 |

### 大屏内容

- 🛰️ 实时战场地图（HTML5 Canvas）
  - 蓝/红/灰/交火光环/✨ Buff 标记
  - HP 条 / 类型标签 / 状态
- 📊 Battle Stats（蓝/红存活、火力优势）
- 🎯 Top Threats（每阵营前 3 大威胁）
- 🧠 Tactical Advisor（每阵营 5 维优势条 + 建议列表）
- 📜 Recent Events（最近 12 条）

---

## 🎮 运行模式

### 1️⃣ 命令行（默认规则引擎）

```bash
java -jar openclaw-wargame-demo-1.2.0.jar 42 25
```

输出：
```
[BLUE tick=0] mode=RULE command: 2 actions | RuleEngine: ...
[t=1] HIT 507ab2 → edf320 dmg=5.0 killed=true
...
Tick: 22 (time=110.0s)
Blue alive: 0  |  Red alive: 2
Result: RED VICTORY
Events published: 87 (dropped=0)
```

### 2️⃣ Web 可视化（推荐）

```bash
java -jar openclaw-wargame-demo-1.2.0.jar 42 60 --web
```

浏览器打开 `http://localhost:18080/` → 看实时战场 + Tactical Advisor

### 3️⃣ RL 训练

```bash
java -jar openclaw-wargame-demo-1.2.0.jar 42 30 --rl --episodes 10
```

输出：
```
=== Episode 1/10 ===
[BLUE tick=0] mode=RL command: 9 actions | RL[aggressive] ε=0.300 states=1
[BLUE tick=18] mode=RL command: 3 actions | RL[retreat] ε=0.300 states=4
  Episode 1 result: RED
    blue ε=0.2985 states=4 updates=14

=== Episode 5/10 ===
  Episode 5 result: DRAW
    blue ε=0.2926 states=8 updates=58

RL Training Report
  Blue wins: 3 (30.0%)
  Red wins:  4 (40.0%)
  Draws:     3 (30.0%)
```

### 4️⃣ 组合（RL + Web）

```bash
java -jar openclaw-wargame-demo-1.2.0.jar 42 60 --rl --episodes 3 --web --port 18080
```

---

## 📜 版本历史

### v1.2.0 (2026-06-25) — AI 实时辅助作战 + 战场优势增强

新增：
- Buff 系统（7 种增益）
- BattleAdvantage（5 维优势量化）
- TacticalAdvisor（9 种建议 + Buff 派发）
- Web 大屏 Tactical Advisor 卡片
- `/api/advisory/{blue|red}` REST 端点
- 地图单位 ✨ Buff 图标

测试：70 → 90 (+20)

### v1.1.0 (2026-06-25) — 强化学习 + Web 可视化

新增：
- `openclaw-wargame-rl` 模块（Q-Learning）
- `openclaw-wargame-web` 模块（HTTP + HTML5）
- `AutonomousCommander.DecisionMode` (RULE/RL/HYBRID)
- Demo: `--web / --port / --rl / --hybrid / --episodes`

测试：46 → 70 (+24)

### v1.0.0 (2026-06-25) — 核心骨架

8 模块 Maven 工程 + 端到端 Demo
46 测试全过

---

## 🛠️ 构建

```bash
# 编译 + 测试
mvn clean test

# 打包（产出 fat jar）
mvn package -DskipTests

# 输出位置
ls -lh openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar
```

Java 17 + Maven 3.8+ 即可。

---

## 📈 路线图

| 版本 | 主题 | 状态 |
|------|------|------|
| V1.0 | 核心骨架 (8 模块 + Demo) | ✅ |
| V1.1 | 强化学习 + Web 可视化 | ✅ |
| V1.2 | AI 战术顾问 + Buff 增益 | ✅ |
| V1.3 | WebSocket 实时推送 | 🔜 |
| V1.4 | DQN 深度强化学习 | 🔜 |
| V1.5 | 接入真实地图 (OSM) | 🔜 |
| V2.0 | 接入真实武器 API | 🔜 |

---

## 🙏 致谢

- **战场模型**：参考兵棋推演设计原则
- **AI 部分**：经典 OODA 决策论 + MCTS + Q-Learning
- **Web 大屏**：JDK 内置 HttpServer（零依赖）
- **工程实践**：继承自 OpenClaw Enterprise 多模块 Maven 架构经验