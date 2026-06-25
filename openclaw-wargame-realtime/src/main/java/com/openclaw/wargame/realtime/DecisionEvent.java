package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;

/**
 * 决策事件。
 */
public final class DecisionEvent extends BattleEvent {
    private final Team team;
    private final String planId;
    private final String summary;
    private final int actionCount;

    public DecisionEvent(long tick, double t, Team team, String planId, String summary, int actionCount) {
        super(tick, t);
        this.team = team;
        this.planId = planId;
        this.summary = summary;
        this.actionCount = actionCount;
    }

    public Team team() { return team; }
    public String planId() { return planId; }
    public String summary() { return summary; }
    public int actionCount() { return actionCount; }

    @Override
    public EventKind kind() { return EventKind.DECISION_MADE; }

    @Override
    public Team[] involvedTeams() {
        return new Team[]{team};
    }
}
