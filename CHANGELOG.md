# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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