package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.realtime.BattleClock;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.openclaw.wargame.simulation.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 整场战斗编排器：让两个 AutonomyLoop 对抗，运行 N tick。
 */
public final class BattleRunner {
    private static final Logger log = LoggerFactory.getLogger(BattleRunner.class);

    private final Simulator simulator;
    private final BattleClock clock;
    private final BattleEventBus eventBus;
    private final AutonomyLoop blue;
    private final AutonomyLoop red;
    private final int maxTicks;
    private final Consumer<BattleState> onTick;
    /** 可选：写入 Advisor 报告用于 Web 展示 */
    private final AdvisorSink advisorSink;
    /** 可选：录制到文件 */
    private final com.openclaw.wargame.simulation.BattleRecorder recorder;

    public interface AdvisorSink {
        void publish(com.openclaw.wargame.analysis.TacticalAdvisor.AdvisoryReport report, Team team);
    }

    public BattleRunner(Simulator simulator, BattleClock clock, BattleEventBus eventBus,
                        AutonomyLoop blue, AutonomyLoop red,
                        int maxTicks, Consumer<BattleState> onTick) {
        this(simulator, clock, eventBus, blue, red, maxTicks, onTick, null, null);
    }

    public BattleRunner(Simulator simulator, BattleClock clock, BattleEventBus eventBus,
                        AutonomyLoop blue, AutonomyLoop red,
                        int maxTicks, Consumer<BattleState> onTick,
                        AdvisorSink advisorSink) {
        this(simulator, clock, eventBus, blue, red, maxTicks, onTick, advisorSink, null);
    }

    public BattleRunner(Simulator simulator, BattleClock clock, BattleEventBus eventBus,
                        AutonomyLoop blue, AutonomyLoop red,
                        int maxTicks, Consumer<BattleState> onTick,
                        AdvisorSink advisorSink,
                        com.openclaw.wargame.simulation.BattleRecorder recorder) {
        this.simulator = simulator;
        this.clock = clock;
        this.eventBus = eventBus;
        this.blue = blue;
        this.red = red;
        this.maxTicks = maxTicks;
        this.onTick = onTick;
        this.advisorSink = advisorSink;
        this.recorder = recorder;
    }

    public BattleState run(BattleState initial) {
        BattleState state = initial;
        if (recorder != null) {
            try {
                recorder.writeHeader(initial);
            } catch (Exception e) {
                log.warn("recorder.writeHeader failed: {}", e.getMessage());
            }
        }
        for (int i = 0; i < maxTicks; i++) {
            // 红蓝各自 tick
            state = blue.tick(state);
            state = red.tick(state);
            // 推进时钟
            clock.advance();
            // 发布 advisor 报告
            if (advisorSink != null) {
                if (blue.lastReport() != null) advisorSink.publish(blue.lastReport(), Team.BLUE);
                if (red.lastReport() != null) advisorSink.publish(red.lastReport(), Team.RED);
            }
            // 录制
            if (recorder != null) {
                try {
                    recorder.recordTick(state);
                } catch (Exception e) {
                    log.warn("recorder.recordTick failed: {}", e.getMessage());
                }
            }
            // 回调
            if (onTick != null) onTick.accept(state);
            // 胜负判定
            BattleResult r = judge(state);
            if (r != null) {
                log.info("Battle ended at tick={}, winner={}", state.tick(), r.winner);
                // episode 结束：用于 RL 衰减 ε + 记录训练历史
                if (blue.commander() != null && blue.commander().rlAgent() != null) {
                    double rw = r.winner == blue.team() ? 100 : (r.winner == Team.NEUTRAL ? 0 : -100);
                    blue.commander().rlAgent().observeReward(state, rw, true);
                    blue.commander().rlAgent().endEpisode(rw);
                }
                if (red.commander() != null && red.commander().rlAgent() != null) {
                    double rw = r.winner == red.team() ? 100 : (r.winner == Team.NEUTRAL ? 0 : -100);
                    red.commander().rlAgent().observeReward(state, rw, true);
                    red.commander().rlAgent().endEpisode(rw);
                }
                if (recorder != null) {
                    try { recorder.writeEnd(r.winner.name(), state.tick()); } catch (Exception ignored) {}
                }
                return state;
            }
        }
        log.info("Battle reached maxTicks={}", maxTicks);
        if (recorder != null) {
            try { recorder.writeEnd("NONE", state.tick()); } catch (Exception ignored) {}
        }
        // episode 结束（超时视为平局）
        if (blue.commander() != null && blue.commander().rlAgent() != null) {
            blue.commander().rlAgent().observeReward(state, 0, true);
            blue.commander().rlAgent().endEpisode(0);
        }
        if (red.commander() != null && red.commander().rlAgent() != null) {
            red.commander().rlAgent().observeReward(state, 0, true);
            red.commander().rlAgent().endEpisode(0);
        }
        return state;
    }

    private BattleResult judge(BattleState state) {
        long blueAlive = state.aliveCount(Team.BLUE);
        long redAlive = state.aliveCount(Team.RED);
        if (blueAlive == 0 && redAlive == 0) {
            return new BattleResult(Team.NEUTRAL, state.tick());
        }
        if (blueAlive == 0) {
            return new BattleResult(Team.RED, state.tick());
        }
        if (redAlive == 0) {
            return new BattleResult(Team.BLUE, state.tick());
        }
        return null;
    }

    public record BattleResult(Team winner, long endTick) {}
}
