package com.openclaw.wargame.ai.decision;

import com.openclaw.wargame.core.team.Team;

import java.util.List;
import java.util.UUID;

/**
 * 决策方案：包含多个 Action，可下发到单位执行。
 */
public final class DecisionPlan {
    private final String id = UUID.randomUUID().toString();
    private final Team team;
    private final long tick;
    private final List<Action> actions;
    private final String summary;

    public DecisionPlan(Team team, long tick, List<Action> actions, String summary) {
        this.team = team;
        this.tick = tick;
        this.actions = List.copyOf(actions);
        this.summary = summary;
    }

    public String id() { return id; }
    public Team team() { return team; }
    public long tick() { return tick; }
    public List<Action> actions() { return actions; }
    public String summary() { return summary; }
}
