package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.ai.mcts.MonteCarloTreeSearch;
import com.openclaw.wargame.ai.rules.RuleEngine;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 无人化指挥官：把 AI 决策方案下发到具体单位。
 * <p>
 * 这是 OODA 循环中的 "Act" 阶段：决策 → 下发 → 单位执行。
 * 支持 3 种决策模式：RULE（规则）、RL（强化学习）、HYBRID（混合）。
 */
public final class AutonomousCommander {
    private static final Logger log = LoggerFactory.getLogger(AutonomousCommander.class);

    public enum DecisionMode { RULE, RL, HYBRID }

    private final Team team;
    private final RuleEngine rules;
    private final MonteCarloTreeSearch mcts;
    private final RLAgent rlAgent; // nullable
    private DecisionMode mode = DecisionMode.RULE;

    public AutonomousCommander(Team team, RuleEngine rules, MonteCarloTreeSearch mcts) {
        this(team, rules, mcts, null);
    }

    public AutonomousCommander(Team team, RuleEngine rules, MonteCarloTreeSearch mcts, RLAgent rlAgent) {
        this.team = team;
        this.rules = rules;
        this.mcts = mcts;
        this.rlAgent = rlAgent;
    }

    public Team team() { return team; }
    public DecisionMode mode() { return mode; }
    public AutonomousCommander setMode(DecisionMode m) { this.mode = m; return this; }
    public RLAgent rlAgent() { return rlAgent; }

    /**
     * 对当前态势制定并下发决策。
     */
    public DecisionPlan command(BattleState state) {
        DecisionPlan plan;
        if (mode == DecisionMode.RL && rlAgent != null) {
            plan = rlAgent.decide(state, team);
        } else if (mode == DecisionMode.HYBRID && rlAgent != null) {
            // 混合：RL 选 Strategy，规则补足细节（不推荐，先 RL）
            plan = rlAgent.decide(state, team);
            // 补规则方案作为兜底
            DecisionPlan rulePlan = rules.decide(state, team);
            // 合并
            java.util.Set<String> units = new java.util.HashSet<>();
            for (Action a : plan.actions()) units.add(a.unitId());
            java.util.List<Action> merged = new java.util.ArrayList<>(plan.actions());
            for (Action a : rulePlan.actions()) {
                if (units.add(a.unitId())) merged.add(a);
            }
            plan = new DecisionPlan(team, state.tick(), merged,
                    "HYBRID: " + plan.summary() + " + " + rulePlan.summary());
        } else {
            plan = rules.decide(state, team);
        }
        log.info("[{} tick={}] mode={} command: {} actions | {}",
                team, state.tick(), mode, plan.actions().size(), plan.summary());
        return plan;
    }

    /**
     * 把决策方案中的动作应用到一个 BattleState 的拷贝上，返回新 BattleState。
     * 用于在仿真主循环中推进。
     */
    public BattleState apply(BattleState state, DecisionPlan plan) {
        Map<String, Unit> byId = new HashMap<>();
        for (Unit u : state.units()) byId.put(u.id(), u);
        for (Action a : plan.actions()) {
            Unit u = byId.get(a.unitId());
            if (u == null || !u.isAlive()) continue;
            if (a instanceof Action.MoveAction m) {
                u.setMoveTarget(m.destination());
            } else if (a instanceof Action.EngageAction) {
                u.setMoveTarget(null);
                u.enterEngagement();
            } else if (a instanceof Action.RetreatAction r) {
                u.setMoveTarget(r.safePosition());
                u.retreat();
            } else if (a instanceof Action.DefendAction d) {
                u.setMoveTarget(d.defendPosition());
            }
        }
        return state;
    }

    /**
     * 同时使用规则 + MCTS 给出"高级策略评估"。
     */
    public StrategicAssessment assess(BattleState state) {
        DecisionPlan plan = rules.decide(state, team);
        MonteCarloTreeSearch.MCTSResult mctsResult = mcts.search(state);
        return new StrategicAssessment(plan, mctsResult);
    }

    public record StrategicAssessment(DecisionPlan rulePlan, MonteCarloTreeSearch.MCTSResult mctsResult) {}
}
