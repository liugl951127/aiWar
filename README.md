# OpenClaw Wargame · AI 自主战争仿真系统

> **军事战争实时分析** + **战争无人化** + **自动决策与反击** + **AI 战术顾问 + 战场优势增强**
> 现已支持 **WebSocket 毫秒级实时推送** 和 **Docker 一键部署**。

基于 Java 17 的完整战争仿真系统，覆盖 OODA 作战循环、武器调度、强化学习、Web 可视化、战术顾问 Buff 增益。

[![Java 17](https://img.shields.io/badge/Java-17-blue)]()
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-orange)]()
[![Tests](https://img.shields.io/badge/Tests-98%20passed-green)]()
[![Modules](https://img.shields.io/badge/Modules-10-purple)]()
[![Docker](https://img.shields.io/badge/Docker-ready-blue)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)]()

**仓库**：https://github.com/liugl951127/aiWar
**最新 Release**：[v1.3.0](https://github.com/liugl951127/aiWar/releases/tag/v1.3.0) · **完整状态**：见 [PROJECT_STATUS.md](PROJECT_STATUS.md)

---

## ⚡ 一键体验

### 方式 1：下载 jar（最快）

```bash
# 下载最新 fat jar (~230K)
curl -L -o wargame.jar \
  https://github.com/liugl951127/aiWar/releases/download/v1.3.0/openclaw-wargame-demo-1.3.0.jar

# 命令行对战
java -jar wargame.jar 42 25

# Web 可视化（WebSocket 实时推送 + REST API + Tactical Advisor + Buff 大屏）
java -jar wargame.jar 42 60 --web
# 浏览器打开 http://localhost:18080/

# RL 训练
java -jar wargame.jar 42 30 --rl --episodes 10
```

### 方式 2：Docker（推荐用于部署）

```bash
docker compose up --build
# 浏览器打开 http://localhost:18080/
```

仅需 Java 17+ 或 Docker，**零外部依赖**。

---

## 🎖️ V1.2 亮点 —— AI 战术顾问 + 战场优势增强

每 tick 自动运行：

```
   战场状态 ──►  BattleAdvantage  ──►  Advice 9 种  ──►  BuffAssignment
                (5 维优势量化)        (战术建议)         (7 种增益分配)
                firepower 35%          ATTACK             FIREPOWER +30%
                manpower  30%          DEFEND             ARMOR     +50%
                detection 15%          FLANK              SPEED     +40%
                mobility  10%          RETREAT            DETECTION +50%
                cohesion  10%          RECON              STEALTH/SUPPRESSION
                overall 100%           RESUPPLY           COORDINATION
                                      COORDINATE_FIRE
                                      EMERGENCY_RETREAT
```

AI 不只做决策，还**主动增强战场优势**。Web 大屏右侧 Advisor 卡片实时显示 5 维优势条和当前建议。

---

## 🏗️ 10 大模块

| 模块 | 能力 |
|------|------|
| **core** | 战场领域模型（10 单位 × 12 武器 × 7 地形）+ **Buff 系统** |
| **realtime** | 无锁事件总线（CAS ring buffer, BLOCK/DROP backpressure） |
| **analysis** | 态势分析 + 威胁评估 + **5 维优势 + 战术顾问** |
| **ai** | 规则引擎（5 rules）+ 蒙特卡洛树搜索（MCTS） |
| **weapon** | 火控计算机 + 交战规则 + 武器调度 |
| **simulation** | 确定性仿真引擎（MCTS 回放用） |
| **rl** | Q-Learning + 216 状态 + 5 策略 |
| **autonomy** | OODA 循环 + RL 智能体 + 战斗编排 |
| **web** | HTTP 服务 + HTML5 Canvas 大屏 + REST API |
| **demo** | CLI 端到端（4 种模式：命令行/Web/RL/混合） |

详见 [PROJECT_STATUS.md](PROJECT_STATUS.md)

---

## 🧠 3 套 AI 大脑（可切换）

| 模式 | 算法 | 何时用 |
|------|------|--------|
| `RULE` | 5 条规则 + 优先级合并 | 生产环境、可解释 |
| `MCTS` | UCB1 + 100 迭代 + 8 步推演 | 战术推演、研究 |
| `RL` | Q-Learning + ε-greedy | 自主学习、博弈 |
| `HYBRID` | RL 选 Strategy + 规则补足 | 兼顾学习与可解释 |

```java
AutonomousCommander cmd = new AutonomousCommander(team, rules, mcts, rlAgent);
cmd.setMode(AutonomousCommander.DecisionMode.RL); // 切换大脑
```

---

## 🎯 实战案例

### 命令行对抗

```bash
$ java -jar wargame.jar 42 25
[BLUE tick=0] mode=RULE command: 2 actions | RuleEngine: ...
[t=1] HIT 507ab2 → edf320 dmg=5.0 killed=true
[t=2] HIT ... killed=true
...
Tick: 22 | Blue alive: 0 | Red alive: 2 | Result: RED VICTORY
Events: 87 (dropped=0)
```

### RL 5 episode 训练报告

```
Episode 1 result: RED    | ε=0.2985 states=4 updates=14
Episode 2 result: RED    | ε=0.2970 states=4 updates=24
Episode 3 result: DRAW   | ε=0.2955 states=6 updates=38
Episode 4 result: BLUE   | ε=0.2941 states=7 updates=54
Episode 5 result: DRAW   | ε=0.2926 states=8 updates=72
```

ε 从 0.300 衰减到 0.293（×0.995^5），Q 表从 1 状态扩展到 8 状态，蓝方开始学会赢！

---

## 📊 数据

| 维度 | 数量 |
|------|------|
| Maven 模块 | 10 |
| Java 文件 | 63 |
| 代码行数 | 5,850 |
| 测试用例 | **90** (全过 ✅) |
| 武器类型 | 12 |
| 单位类型 | 10 |
| 地形类型 | 7 |
| Buff 类型 | 7 |
| Strategy 数 | 5 |
| RL 状态数 | 216 |
| 战术建议 | 9 种 |

---

## 🧪 测试

```bash
mvn test
```

输出：
```
OpenClaw Wargame Core ............................. SUCCESS [24 tests]
OpenClaw Wargame Realtime ......................... SUCCESS [4 tests]
OpenClaw Wargame Analysis ......................... SUCCESS [16 tests]
OpenClaw Wargame AI ............................... SUCCESS [4 tests]
OpenClaw Wargame Weapon ........................... SUCCESS [4 tests]
OpenClaw Wargame RL ............................... SUCCESS [18 tests]
OpenClaw Wargame Autonomy ......................... SUCCESS [6 tests]
OpenClaw Wargame Web .............................. SUCCESS [6 tests]
```

---

## 🌐 Web 大屏

启动后浏览器访问 `http://localhost:18080/`：

- 🛰️ **实时战场地图**：蓝/红/灰单位 + 交火光环 + ✨ Buff 标记 + HP 条
- 📊 **Battle Stats**：存活数、火力优势比
- 🎯 **Top Threats**：每阵营前 3 大威胁
- 🧠 **Tactical Advisor**：5 维优势条 + 9 种建议列表
- 📜 **Recent Events**：最近 12 条事件流
- 🔵 **WebSocket 指示器**：实时推送状态（vs POLL 回退模式）

REST API (HTTP 18080)：
- `GET /api/snapshot` — 完整快照
- `GET /api/analysis/{blue|red}` — 态势分析
- `GET /api/advisory/{blue|red}` — 战术顾问报告
- `GET /api/events` — 最近事件

WebSocket (port 18081)：
- `ws://localhost:18081/ws/snapshot` — 主动推送 snapshot（毫秒级实时）
- 客户端消息 `"ping"` → 服务端回 `"pong"`
- 失败自动降级到 500ms REST 轮询

---

## 📜 版本历史

- **v1.3.0** (2026-06-25) — WebSocket 毫秒级实时推送 + Docker 一键部署 + GitHub Actions CI + Q 表 CSV 导出
- **v1.2.0** (2026-06-25) — AI 战术顾问 + 战场优势增强 + Buff 系统 + Web 集成
- **v1.1.0** (2026-06-25) — 强化学习 (Q-Learning) + Web 可视化 + 3 套 AI 大脑切换
- **v1.0.0** (2026-06-25) — 核心骨架（8 模块）+ OODA 循环 + 端到端 Demo

详见 [CHANGELOG.md](CHANGELOG.md)

---

## 🛣️ 路线图

- ✅ V1.0 — 核心骨架
- ✅ V1.1 — RL + Web 可视化
- ✅ V1.2 — AI 战术顾问 + Buff
- ✅ V1.3 — WebSocket 推送 + Docker + CI
- 🔜 V1.4 — DQN 深度强化学习
- 🔜 V1.5 — 真实地图接入 (OpenStreetMap)
- 🔜 V2.0 — 真实武器 API 集成

---

## 📜 License

Apache 2.0

---

## 🙏 致谢

- 战场模型参考兵棋推演设计原则
- AI 部分采用经典 OODA + MCTS + Q-Learning
- 工程实践继承自 OpenClaw Enterprise