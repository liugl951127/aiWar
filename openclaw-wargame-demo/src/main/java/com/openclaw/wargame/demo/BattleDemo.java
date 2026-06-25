package com.openclaw.wargame.demo;

import com.openclaw.wargame.analysis.SituationalAnalysis;
import com.openclaw.wargame.autonomy.AutonomousCommander;
import com.openclaw.wargame.autonomy.AutonomyLoop;
import com.openclaw.wargame.autonomy.BattleRunner;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.Terrain;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import com.openclaw.wargame.realtime.BattleClock;
import com.openclaw.wargame.realtime.BattleEvent;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.openclaw.wargame.simulation.Simulator;
import com.openclaw.wargame.web.BattleStateHolder;
import com.openclaw.wargame.web.WargameServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端 Demo：构造一场红蓝对抗，让两方自主决策系统自动对战，并打印战报。
 * <pre>
 *   # 命令行模式
 *   java -jar openclaw-wargame-demo.jar 42 30
 *
 *   # 启用 Web 可视化（默认 18080 端口）
 *   java -jar openclaw-wargame-demo.jar 42 30 --web
 *   # 浏览器打开 http://localhost:18080/
 *
 *   # 启用 RL 训练（蓝方用 RL 红方用规则）
 *   java -jar openclaw-wargame-demo.jar 42 30 --rl
 * </pre>
 */
public final class BattleDemo {

    public static void main(String[] args) throws Exception {
        long seed = 42L;
        int maxTicks = 30;
        boolean webEnabled = false;
        int webPort = 18080;
        boolean rlEnabled = false;
        int rlEpisodes = 1;
        AutonomousCommander.DecisionMode blueMode = AutonomousCommander.DecisionMode.RULE;
        AutonomousCommander.DecisionMode redMode = AutonomousCommander.DecisionMode.RULE;

        // 解析参数
        int argIdx = 0;
        if (args.length > argIdx) seed = Long.parseLong(args[argIdx++]);
        if (args.length > argIdx) maxTicks = Integer.parseInt(args[argIdx++]);
        while (argIdx < args.length) {
            switch (args[argIdx]) {
                case "--web" -> webEnabled = true;
                case "--port" -> webPort = Integer.parseInt(args[++argIdx]);
                case "--rl" -> { rlEnabled = true; blueMode = AutonomousCommander.DecisionMode.RL; }
                case "--hybrid" -> { rlEnabled = true; blueMode = AutonomousCommander.DecisionMode.HYBRID; }
                case "--episodes" -> rlEpisodes = Integer.parseInt(args[++argIdx]);
                case "--rl-red" -> redMode = AutonomousCommander.DecisionMode.RL;
                default -> System.err.println("Unknown arg: " + args[argIdx]);
            }
            argIdx++;
        }

        System.out.println("===============================================================");
        System.out.println("  OpenClaw Wargame - Autonomous Battle Demo");
        System.out.println("  seed=" + seed + "  maxTicks=" + maxTicks
                + "  blueMode=" + blueMode + "  redMode=" + redMode
                + "  rlEpisodes=" + rlEpisodes
                + (webEnabled ? "  WEB=http://localhost:" + webPort : ""));
        System.out.println("===============================================================");

        // 1. 构造 8km x 8km 战场
        TerrainMap map = new TerrainMap(8000, 8000, 100);
        for (double x = 3000; x < 5000; x += 100) {
            for (double y = 3000; y < 5000; y += 100) {
                map.setTerrain(new Position(x, y), Terrain.FOREST);
            }
        }
        for (double x = 3700; x < 4300; x += 100) {
            for (double y = 3700; y < 4300; y += 100) {
                map.setTerrain(new Position(x, y), Terrain.URBAN);
            }
        }

        List<Unit> blue = buildForce(Team.BLUE, new Position(500, 500), map);
        List<Unit> red = buildForce(Team.RED, new Position(7300, 7300), map);

        BattleState initial = Simulator.createInitial(map, blue, red, List.of());

        BattleEventBus bus = new BattleEventBus(1024, BattleEventBus.BackpressurePolicy.BLOCK);
        BattleClock clock = new BattleClock(5.0);
        Simulator sim = new Simulator(5.0, seed);

        // 可视化
        BattleStateHolder holder = new BattleStateHolder();
        WargameServer webServer = null;
        if (webEnabled) {
            webServer = new WargameServer(webPort, holder, bus);
            webServer.start();
        }

        // 2. 订阅事件（终端 HIT/KILL 输出）
        bus.subscribe(event -> {
            if (event.kind() == BattleEvent.EventKind.HIT) {
                System.out.printf("[t=%4d] HIT   %s → %s dmg=%.1f killed=%s%n",
                        event.tick(),
                        ((com.openclaw.wargame.realtime.HitEvent) event).shooterId().substring(0, 6),
                        ((com.openclaw.wargame.realtime.HitEvent) event).targetId().substring(0, 6),
                        ((com.openclaw.wargame.realtime.HitEvent) event).damage(),
                        ((com.openclaw.wargame.realtime.HitEvent) event).killed());
            }
        });

        // 3. 跑多 episode（RL 训练用）
        final int maxEpisodes = Math.max(1, rlEpisodes);
        BattleState finalState = initial;
        int blueWins = 0, redWins = 0;
        for (int ep = 0; ep < maxEpisodes; ep++) {
            final int currentEp = ep;
            if (maxEpisodes > 1) {
                System.out.println("=== Episode " + (currentEp + 1) + "/" + maxEpisodes + " ===");
            }
            // 每个 episode 重新生成双方军队（保持公平）
            List<Unit> blue2 = buildForce(Team.BLUE, new Position(500, 500), map);
            List<Unit> red2 = buildForce(Team.RED, new Position(7300, 7300), map);
            BattleState init = Simulator.createInitial(map, blue2, red2, List.of());

            final long blueSeed = seed + currentEp;
            final long redSeed = seed + 100 + currentEp;
            final boolean singleEpisode = (maxEpisodes == 1);
            AutonomyLoop blueLoop = AutonomyLoop.createWithMode(Team.BLUE, sim, clock, bus, blueSeed,
                    blueMode, rlEnabled);
            AutonomyLoop redLoop = AutonomyLoop.createWithMode(Team.RED, sim, clock, bus, redSeed,
                    redMode, rlEnabled);

            holder.setRunning(true);
            final com.openclaw.wargame.autonomy.BattleRunner.AdvisorSink sink = (webEnabled)
                    ? (report, team) -> holder.setReport(team, report)
                    : null;
            BattleRunner runner = new BattleRunner(sim, clock, bus, blueLoop, redLoop, maxTicks, state -> {
                holder.set(state);
                if (state.tick() % 10 == 0 && singleEpisode) {
                    printSituation(state, Team.BLUE);
                    printSituation(state, Team.RED);
                    System.out.println("---------------------------------------------------------------");
                }
            }, sink);
            finalState = runner.run(init);
            holder.setRunning(false);

            long ba = finalState.aliveCount(Team.BLUE);
            long ra = finalState.aliveCount(Team.RED);
            String winner = ba == 0 ? "RED" : ra == 0 ? "BLUE" : "DRAW";
            if ("BLUE".equals(winner)) blueWins++;
            else if ("RED".equals(winner)) redWins++;
            holder.setWinner(winner, finalState.tick());

            if (maxEpisodes > 1) {
                System.out.printf("  Episode %d result: %s (blue=%d red=%d) %n",
                        currentEp + 1, winner, ba, ra);
                if (blueLoop.commander().rlAgent() != null) {
                    System.out.printf("    blue ε=%.4f states=%d updates=%d%n",
                            blueLoop.commander().rlAgent().learner().epsilon(),
                            blueLoop.commander().rlAgent().learner().tableSize(),
                            blueLoop.commander().rlAgent().learner().updateCount());
                }
                if (redLoop.commander().rlAgent() != null) {
                    System.out.printf("    red  ε=%.4f states=%d updates=%d%n",
                            redLoop.commander().rlAgent().learner().epsilon(),
                            redLoop.commander().rlAgent().learner().tableSize(),
                            redLoop.commander().rlAgent().learner().updateCount());
                }
            }
        }

        // 4. 战报
        if (rlEpisodes == 1) {
            printBattleReport(finalState, bus);
        } else {
            printTrainingReport(blueWins, redWins, rlEpisodes);
        }

        if (webEnabled) {
            System.out.println();
            System.out.println("Web dashboard running at http://localhost:" + webPort + "/");
            System.out.println("Press Ctrl+C to exit...");
            // 阻塞主线程直到用户终止
            Thread.currentThread().join();
        }
        if (webServer != null) webServer.stop();
    }

    private static List<Unit> buildForce(Team team, Position baseCorner, TerrainMap map) {
        List<Unit> force = new ArrayList<>();
        Unit cmd = Unit.create(UnitType.COMMAND, team, baseCorner);
        force.add(cmd);
        for (int i = 0; i < 4; i++) {
            Unit armor = Unit.create(UnitType.ARMOR, team,
                    new Position(baseCorner.x() + 200 + i * 150, baseCorner.y() + 100));
            armor.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            armor.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 500));
            force.add(armor);
        }
        for (int i = 0; i < 2; i++) {
            Unit aa = Unit.create(UnitType.ANTI_AIR, team,
                    new Position(baseCorner.x() + 100, baseCorner.y() + 300 + i * 200));
            aa.mountWeapon(new Weapon(WeaponType.ANTI_AIR_MISSILE, 12));
            force.add(aa);
        }
        Unit artillery = Unit.create(UnitType.ARTILLERY, team,
                new Position(baseCorner.x() + 400, baseCorner.y() + 400));
        artillery.mountWeapon(new Weapon(WeaponType.ARTILLERY_SHELL, 20));
        force.add(artillery);
        Unit recon = Unit.create(UnitType.RECON, team,
                new Position(baseCorner.x() + 600, baseCorner.y() + 200));
        recon.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 200));
        force.add(recon);
        return force;
    }

    private static void printSituation(BattleState state, Team team) {
        SituationalAnalysis sa = new SituationalAnalysis(state, team);
        long alive = state.aliveCount(team);
        double ratio = sa.firepowerRatio();
        System.out.printf("[%s] t=%d alive=%d firepower-ratio=%.2f top-threats=%d nearest-enemy=%s%n",
                team, state.tick(), alive, ratio,
                sa.topThreats(3).size(),
                sa.nearestEnemy() != null ? sa.nearestEnemy().id().substring(0, 6) : "none");
    }

    private static void printBattleReport(BattleState state, BattleEventBus bus) {
        long blueAlive = state.aliveCount(Team.BLUE);
        long redAlive = state.aliveCount(Team.RED);
        System.out.println();
        System.out.println("===============================================================");
        System.out.println("  Battle Report");
        System.out.println("===============================================================");
        System.out.printf("  Tick: %d (time=%.1fs)%n", state.tick(), state.timeSeconds());
        System.out.printf("  Blue alive: %d  |  Red alive: %d%n", blueAlive, redAlive);
        System.out.printf("  Blue firepower ratio: %.2f%n", new SituationalAnalysis(state, Team.BLUE).firepowerRatio());
        System.out.printf("  Red firepower ratio:  %.2f%n", new SituationalAnalysis(state, Team.RED).firepowerRatio());
        System.out.printf("  Events published: %d (dropped=%d)%n", bus.publishedCount(), bus.droppedCount());
        if (blueAlive == 0 && redAlive == 0) System.out.println("  Result: DRAW (both eliminated)");
        else if (blueAlive == 0) System.out.println("  Result: RED VICTORY");
        else if (redAlive == 0) System.out.println("  Result: BLUE VICTORY");
        else if (blueAlive > redAlive) System.out.println("  Result: BLUE leading (unfinished)");
        else if (redAlive > blueAlive) System.out.println("  Result: RED leading (unfinished)");
        else System.out.println("  Result: EVEN");
        System.out.println("===============================================================");
    }

    private static void printTrainingReport(int blueWins, int redWins, int episodes) {
        System.out.println();
        System.out.println("===============================================================");
        System.out.println("  RL Training Report");
        System.out.println("===============================================================");
        System.out.printf("  Episodes: %d%n", episodes);
        System.out.printf("  Blue wins: %d (%.1f%%)%n", blueWins, 100.0 * blueWins / episodes);
        System.out.printf("  Red wins:  %d (%.1f%%)%n", redWins, 100.0 * redWins / episodes);
        System.out.printf("  Draws:     %d%n", episodes - blueWins - redWins);
        System.out.println("===============================================================");
    }
}
