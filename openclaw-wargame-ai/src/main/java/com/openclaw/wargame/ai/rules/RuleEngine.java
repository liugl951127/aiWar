package com.openclaw.wargame.ai.rules;

import com.openclaw.wargame.analysis.SituationalAnalysis;
import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 规则引擎：组合多个 Rule，按 priority 合并去重，返回统一 DecisionPlan。
 */
public final class RuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules = new ArrayList<>();

    public RuleEngine register(Rule rule) {
        rules.add(rule);
        return this;
    }

    public List<Rule> rules() {
        return List.copyOf(rules);
    }

    public DecisionPlan decide(BattleState state, Team team) {
        SituationalAnalysis analysis = new SituationalAnalysis(state, team);
        List<Action> all = new ArrayList<>();
        for (Rule r : rules) {
            Team applicable = r.applicableTeam();
            if (applicable != null && applicable != team) continue;
            try {
                List<Action> actions = r.evaluate(state, analysis);
                if (actions != null) all.addAll(actions);
            } catch (Exception e) {
                log.warn("rule {} failed: {}", r.name(), e.getMessage());
            }
        }
        // 按 priority 降序，按 unitId 分组只保留最高优先级
        all.sort(Comparator.comparingDouble(Action::priority).reversed());
        List<Action> dedup = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Action a : all) {
            if (seen.add(a.unitId())) dedup.add(a);
        }
        String summary = String.format("RuleEngine: %d actions (from %d candidates)", dedup.size(), all.size());
        return new DecisionPlan(team, state.tick(), dedup, summary);
    }
}
