package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.weapon.WeaponType;

/**
 * 武器开火事件。
 */
public final class WeaponFiredEvent extends BattleEvent {
    private final String shooterId;
    private final String weaponId;
    private final WeaponType weaponType;
    private final String targetId;
    private final double distance;

    public WeaponFiredEvent(long tick, double t, String shooterId, String weaponId,
                            WeaponType weaponType, String targetId, double distance) {
        super(tick, t);
        this.shooterId = shooterId;
        this.weaponId = weaponId;
        this.weaponType = weaponType;
        this.targetId = targetId;
        this.distance = distance;
    }

    public String shooterId() { return shooterId; }
    public String weaponId() { return weaponId; }
    public WeaponType weaponType() { return weaponType; }
    public String targetId() { return targetId; }
    public double distance() { return distance; }

    @Override
    public EventKind kind() { return EventKind.WEAPON_FIRED; }

    @Override
    public Team[] involvedTeams() {
        return new Team[0];
    }
}
