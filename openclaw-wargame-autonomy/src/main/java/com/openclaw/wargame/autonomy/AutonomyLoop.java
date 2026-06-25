package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.ai.rules.DefaultRules;
import com.openclaw.wargame.ai.rules.RuleEngine;
import com.openclaw.wargame.ai.mcts.MonteCarloTreeSearch;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.analysis.TacticalAdvisor;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.realtime.BattleClock;
import com.openclaw.wargame.realtime.BattleEvent;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.openclaw.wargame.realtime.DecisionEvent;
import com.openclaw.wargame.rl.QLearner;
import com.openclaw.wargame.rl.StrategyInterpreter;
import com.openclaw.wargame.simulation.Simulator;
import com.openclaw.wargame.weapon.Engagement;
import com.openclaw.wargame.weapon.FireControlComputer;
import com.openclaw.wargame.weapon.RulesOfEngagement;
import com.openclaw.wargame.weapon.WeaponScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 无人化作战循环 (OODA Loop)：
 * <pre>
 *   Observe (观察) → Orient (定位) → Decide (决策) → Act (行动)
 *      ↑                                                    │
 *      └────────────────────────────────────────────────────┘
 * </pre>
 * <ul>
 *   <li><b>Observe</b>：从 BattleEventBus 读取最新事件</li>
 *   <li><b>Orient</b>：用 SituationalAnalysis 评估威胁/弱点</li>
 *   <li><b>Decide</b>：RuleEngine + MCTS 给出决策方案</li>
 *   <li><b>Act</b>：下发 Action 到单位，并触发武器开火</li>
 * </ul>
 * 两方阵营各自运行一个 AutonomyLoop，互相对抗。
 */
public final class AutonomyLoop {
    private static final Logger log = LoggerFactory.getLogger(AutonomyLoop.class);

    private final Team team;
    private final AutonomousCommander commander;
    private final FireControlComputer fireControl;
    private final WeaponScheduler weaponScheduler;
    private final Simulator simulator;
    private final BattleClock clock;
    private final BattleEventBus eventBus;
    private final TacticalAdvisor advisor;

    private DecisionPlan lastPlan;
    private long lastDecisionTick = -1;
    private TacticalAdvisor.AdvisoryReport lastReport;

    public AutonomyLoop(Team team,
                        AutonomousCommander commander,
                        FireControlComputer fireControl,
                        WeaponScheduler weaponScheduler,
                        Simulator simulator,
                        BattleClock clock,
                        BattleEventBus eventBus,
                        TacticalAdvisor advisor) {
        this.team = team;
        this.commander = commander;
        this.fireControl = fireControl;
        this.weaponScheduler = weaponScheduler;
        this.simulator = simulator;
        this.clock = clock;
        this.eventBus = eventBus;
        this.advisor = advisor;
    }

    public Team team() { return team; }
    public DecisionPlan lastPlan() { return lastPlan; }
    public AutonomousCommander commander() { return commander; }
    public TacticalAdvisor advisor() { return advisor; }
    public TacticalAdvisor.AdvisoryReport lastReport() { return lastReport; }

    /**
     * 标准工厂：构造一个配置好的自主循环（默认使用规则引擎）。
     */
    public static AutonomyLoop create(Team team, Simulator simulator, BattleClock clock, BattleEventBus bus, long seed) {
        return createWithMode(team, simulator, clock, bus, seed, AutonomousCommander.DecisionMode.RULE, false);
    }

    /**
     * 带 RL 模式的工厂。
     *
     * @param mode        决策模式：RULE / RL / HYBRID
     * @param rlTraining  true=训练 Q 表（用 ε-greedy + 更新 Q）；false=用 Q 表贪心选
     */
    public static AutonomyLoop createWithMode(Team team, Simulator simulator, BattleClock clock, BattleEventBus bus,
                                              long seed, AutonomousCommander.DecisionMode mode, boolean rlTraining) {
        RuleEngine rules = DefaultRules.createDefault();
        MonteCarloTreeSearch.SimulationFn fn = new MonteCarloTreeSearch.SimulationFn() {
            @Override
            public BattleState stepOne(BattleState state, String preferredUnitId) {
                return simulator.stepOne(state, preferredUnitId);
            }
            @Override
            public BattleState randomStep(BattleState state, java.util.Random rng) {
                return simulator.randomStep(state, rng);
            }
        };
        MonteCarloTreeSearch mcts = new MonteCarloTreeSearch(fn, team, 100, 8, seed);
        QLearner learner = new QLearner(seed);
        StrategyInterpreter interpreter = new StrategyInterpreter(seed + 1);
        RLAgent rlAgent = new RLAgent(team, learner, interpreter, rlTraining);
        AutonomousCommander cmd = new AutonomousCommander(team, rules, mcts, rlAgent).setMode(mode);
        RulesOfEngagement roe = new RulesOfEngagement().setMode(RulesOfEngagement.Mode.WEAPONS_FREE);
        FireControlComputer fcc = new FireControlComputer(roe);
        WeaponScheduler ws = new WeaponScheduler(bus, seed + 1);
        TacticalAdvisor advisor = new TacticalAdvisor(team);
        return new AutonomyLoop(team, cmd, fcc, ws, simulator, clock, bus, advisor);
    }

    /**
     * 单步推进（一个 OODA 循环）。
     */
    public BattleState tick(BattleState state) {
        // 1. Observe: 取最近事件（已经在 eventBus 中）
        List<BattleEvent> recent = eventBus.recent(64);

        // 2. Orient: TacticalAdvisor 生成建议 + Buff 分配
        TacticalAdvisor.AdvisoryReport report = advisor.advise(state);
        lastReport = report;
        // 应用 buff 到 state（在 commander 命令前应用，让命令考虑 buff 后的属性）
        TacticalAdvisor.applyBuffs(state, report.buffAssignments());

        // 3. Decide: 规则 + MCTS
        DecisionPlan plan = commander.command(state);
        lastPlan = plan;
        lastDecisionTick = state.tick();

        // 发布决策事件
        eventBus.publish(new DecisionEvent(
                state.tick(), clock.currentTime(),
                team, plan.id(), plan.summary(), plan.actions().size()
        ));

        // 4. Act: 应用动作 + 火控开火
        BattleState next = commander.apply(state, plan);
        BattleState prevForReward = state;
        next = simulator.stepOne(next, null);

        // 火控开火
        List<Engagement> engagements = fireControl.compute(next, team);
        int hits = weaponScheduler.fireOneRound(next, team, engagements, next.tick(), next.timeSeconds());
        if (!engagements.isEmpty()) {
            log.debug("[{}] tick={} engaged {} targets, hits={}", team, next.tick(), engagements.size(), hits);
        }

        // 5. RL 训练：发送 reward
        if (commander.mode() != AutonomousCommander.DecisionMode.RULE && commander.rlAgent() != null) {
            double reward = StrategyInterpreter.reward(prevForReward, next, team);
            commander.rlAgent().observeReward(next, reward, false);
        }
        return next;
    }
}
