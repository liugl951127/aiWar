package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.UnitType;

/**
 * 探测事件：单位 A 在某处探测到单位 B。
 */
public final class DetectionEvent extends BattleEvent {
    private final String detectorId;
    private final String targetId;
    private final Team detectorTeam;
    private final Team targetTeam;
    private final UnitType targetType;
    private final double distance;

    public DetectionEvent(long tick, double t, String detectorId, Team detectorTeam,
                          String targetId, Team targetTeam, UnitType targetType, double distance) {
        super(tick, t);
        this.detectorId = detectorId;
        this.detectorTeam = detectorTeam;
        this.targetId = targetId;
        this.targetTeam = targetTeam;
        this.targetType = targetType;
        this.distance = distance;
    }

    public String detectorId() { return detectorId; }
    public String targetId() { return targetId; }
    public Team detectorTeam() { return detectorTeam; }
    public Team targetTeam() { return targetTeam; }
    public UnitType targetType() { return targetType; }
    public double distance() { return distance; }

    @Override
    public EventKind kind() { return EventKind.DETECTION; }

    @Override
    public Team[] involvedTeams() {
        return new Team[]{detectorTeam, targetTeam};
    }
}
