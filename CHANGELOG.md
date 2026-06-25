# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-06-25

### Added
- **`WebSocketServer`** — Minimal RFC 6455 WebSocket server (zero deps, JDK built-in `ServerSocket` + manual frame parser)
  - Path `ws://host:18081/ws/snapshot` — pushes snapshot on every state change (100ms poll for change detection)
  - Auto-broadcasts to all connected clients
  - Ping/Pong frame support
  - Auto-cleanup on client disconnect
- **`WebSocketServerTest`** — 5 tests covering connect / initial frame / push on change / ping-pong / multi-client
- **JS dashboard** — Auto-detects WebSocket vs REST polling fallback, shows WS/POLL indicator badge
- **`Dockerfile`** — Multi-stage build (Maven builder → JRE 17 runtime), non-root user, healthcheck, exposes 18080+18081
- **`docker-compose.yml`** — One-command deployment with port mapping + memory limits + restart policy
- **`.github/workflows/ci.yml`** — Build + test on every push, package fat jar artifact
- **`.github/workflows/docker.yml`** — Build & push Docker image to GHCR on tag
- **`QLearner.dumpCsv()`** — Export Q-table to CSV (state_code, strategy, value)
- **`QLearner.snapshot()`** — Defensive copy of Q-table
- **`QLearner.TrainingStats`** + `recordEpisodeStats()` — Per-episode reward/epsilon/size tracking for learning curves

### Stats
- Tests: 90 → **98** (+8: 5 WS + 3 RL observability)
- Java files: 63 → 68 (+5: WebSocketServer + test + 4 Q-Learner methods + workflow)
- Code: 5,850 → ~6,500 lines

## [1.2.1] - 2026-06-25

### Added
- **`PROJECT_STATUS.md`** — Comprehensive project status report (capability matrix, architecture diagrams, OODA flow, decision logic pseudocode, all REST endpoints, version history, roadmap)

### Changed
- **`README.md`** — Simplified into a high-level landing page; delegates to PROJECT_STATUS.md for full details
  - Quickstart one-liner: `curl` + `java -jar`
  - V1.2 highlight section at top
  - Trimmed from 8KB → 5KB while preserving essentials

## [1.2.0] - 2026-06-25

### Added
- **`Buff` system in core** — Temporary unit stat modifiers (7 kinds: FIREPOWER, ARMOR, SPEED, DETECTION, STEALTH, COORDINATION, SUPPRESSION)
- **`Unit.effective*()` methods** — Getter for buff-modified stats (effectiveFirepower/Speed/DetectionRange, stealthFactor, suppressionFactor)
- **`BattleAdvantage`** — Multi-dimensional battlefield superiority assessment (5 axes: firepower, manpower, detection, mobility, cohesion + overall score)
- **`TacticalAdvisor`** — Real-time AI combat assistant: generates `Advice` (ATTACK / DEFEND / FLANK / RETREAT / RESUPPLY / etc.) + `BuffAssignment` per tick
- **`Unit.tickBuffs()`** — Buff duration decay on each simulation tick
- **`Simulator` buff propagation** — Buffs are copied during `shallowCopy()` and decayed during `stepOne()`
- **Web dashboard** — Tactical Advisor cards with 5-axis advantage bars and color-coded advice list
- **`/api/advisory/{blue|red}`** REST endpoint exposing live advisor reports
- **Map buffs visualization** — Yellow sparkle ✨ icon on units with active buffs

### Stats
- Tests: 70 → **90** (+20: 8 buff + 5 advantage + 7 advisor)

## [1.1.0] - 2026-06-25

### Added
- **`openclaw-wargame-rl`** — Reinforcement learning decision module
  - `QLearner`: ε-greedy Q-Learning with epsilon decay
  - `StateFeatures`: 216-state discretization of battlefield situation
  - `Strategy`: 5 high-level strategies (AGGRESSIVE / DEFENSIVE / FLANKING / RETREAT / RECON_FOCUS)
  - `StrategyInterpreter`: maps Strategy to concrete Actions
- **`openclaw-wargame-web`** — HTTP visualization dashboard
  - `WargameServer`: JDK-built-in `com.sun.net.httpserver` HTTP server (zero external dependencies)
  - REST API: `/api/snapshot`, `/api/analysis/{blue|red}`, `/api/events`
  - HTML5 Canvas real-time battle map
  - Static dashboard with firepower ratio / top threats / event stream
- **`AutonomousCommander.DecisionMode`** — Switch between RULE / RL / HYBRID
- **`RLAgent`** — Wraps QLearner + StrategyInterpreter, supports training and inference
- **`AutonomyLoop.createWithMode(...)`** — Factory supporting RL mode + training flag
- **`BattleDemo`** — CLI flags: `--web`, `--port`, `--rl`, `--hybrid`, `--episodes`

### Changed
- `BattleRunner` now calls `rlAgent.observeReward()` and `endEpisode()` on termination
- `AutonomyLoop.tick()` now feeds reward signal to RL agent

### Stats
- Modules: 9 → **10**
- Java files: 44 → **56** (+12)
- Code: 3,381 → **4,947** lines (+1,566)
- Tests: 46 → **70** (+24, all passing)

## [1.0.0] - 2026-06-25

### Added
- Initial release: 8-module Maven multi-module project
- Battlefield domain model: Unit, Weapon, TerrainMap, BattleState, Team
- Real-time event bus (lock-free ring buffer)
- Situational analysis (threat assessment, weakness detection)
- Rule engine (5 default rules) + Monte Carlo Tree Search
- Fire control computer + Rules of Engagement (ROE)
- Deterministic simulation engine
- OODA autonomous combat loop (Observe → Orient → Decide → Act)
- End-to-end CLI demo with HIT/KILL event stream
- 46 unit tests across 6 modules