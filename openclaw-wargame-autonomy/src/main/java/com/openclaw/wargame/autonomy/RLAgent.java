package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.rl.QLearner;
import com.openclaw.wargame.rl.StateFeatures;
import com.openclaw.wargame.rl.Strategy;
import com.openclaw.wargame.rl.StrategyInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 强化学习 Agent：封装 QLearner + StrategyInterpreter，
 * 提供训练 / 决策接口。
 */
public final class RLAgent {
    private static final Logger log = LoggerFactory.getLogger(RLAgent.class);

    private final Team team;
    private final QLearner learner;
    private final StrategyInterpreter interpreter;
    private final boolean training;

    private StateFeatures lastState;
    private Strategy lastStrategy;
    private BattleState lastBattleState;

    public RLAgent(Team team, QLearner learner, StrategyInterpreter interpreter, boolean training) {
        this.team = team;
        this.learner = learner;
        this.interpreter = interpreter;
        this.training = training;
    }

    public Team team() { return team; }
    public QLearner learner() { return learner; }

    /**
     * 选择策略并生成 DecisionPlan。
     */
    public DecisionPlan decide(BattleState state, Team team) {
        StateFeatures sf = StateFeatures.extract(state, team);
        Strategy strategy = training ? learner.selectAction(sf) : learner.bestAction(sf);
        List<Action> actions = interpreter.interpret(strategy, state, team);
        String summary = String.format("RL[%s] ε=%.3f states=%d", strategy.code(),
                learner.epsilon(), learner.tableSize());
        // 训练模式：保存状态用于后续更新
        if (training) {
            lastState = sf;
            lastStrategy = strategy;
            lastBattleState = state;
        }
        return new DecisionPlan(team, state.tick(), actions, summary);
    }

    /**
     * 接收奖励信号，更新 Q 表（仅训练模式）。
     */
    public void observeReward(BattleState nextState, double reward, boolean terminal) {
        if (!training || lastState == null) return;
        StateFeatures nextSf = StateFeatures.extract(nextState, team);
        learner.update(lastState, lastStrategy, reward, nextSf, terminal);
        if (terminal) {
            lastState = null;
            lastStrategy = null;
            lastBattleState = null;
        } else {
            // 滑动：把 next 当作新的 last
            lastState = nextSf;
            lastStrategy = learner.bestAction(nextSf);
            lastBattleState = nextState;
        }
    }

    /**
     * episode 结束（用于衰减 ε）。
     */
    public void endEpisode() {
        learner.endEpisode();
    }
}
