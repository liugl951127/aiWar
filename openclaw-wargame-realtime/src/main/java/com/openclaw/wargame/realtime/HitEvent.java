package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;

/**
 * 命中事件。
 */
public final class HitEvent extends BattleEvent {
    private final String shooterId;
    private final String targetId;
    private final Team shooterTeam;
    private final Team targetTeam;
    private final double damage;
    private final boolean killed;

    public HitEvent(long tick, double t, String shooterId, Team shooterTeam,
                    String targetId, Team targetTeam, double damage, boolean killed) {
        super(tick, t);
        this.shooterId = shooterId;
        this.shooterTeam = shooterTeam;
        this.targetId = targetId;
        this.targetTeam = targetTeam;
        this.damage = damage;
        this.killed = killed;
    }

    public String shooterId() { return shooterId; }
    public String targetId() { return targetId; }
    public Team shooterTeam() { return shooterTeam; }
    public Team targetTeam() { return targetTeam; }
    public double damage() { return damage; }
    public boolean killed() { return killed; }

    @Override
    public EventKind kind() { return EventKind.HIT; }

    @Override
    public Team[] involvedTeams() {
        return new Team[]{shooterTeam, targetTeam};
    }
}
