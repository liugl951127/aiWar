package com.openclaw.wargame.weapon;

import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.weapon.Weapon;

import java.util.Objects;
import java.util.UUID;

/**
 * 单次交火记录：哪个单位用哪把武器打哪个目标。
 */
public final class Engagement {
    private final String id = UUID.randomUUID().toString();
    private final Unit shooter;
    private final Weapon weapon;
    private final Unit target;
    private final double distance;
    private final double probabilityOfHit;
    private final double expectedDamage;

    public Engagement(Unit shooter, Weapon weapon, Unit target, double distance,
                      double probabilityOfHit, double expectedDamage) {
        this.shooter = Objects.requireNonNull(shooter);
        this.weapon = Objects.requireNonNull(weapon);
        this.target = Objects.requireNonNull(target);
        this.distance = distance;
        this.probabilityOfHit = probabilityOfHit;
        this.expectedDamage = expectedDamage;
    }

    public String id() { return id; }
    public Unit shooter() { return shooter; }
    public Weapon weapon() { return weapon; }
    public Unit target() { return target; }
    public double distance() { return distance; }
    public double probabilityOfHit() { return probabilityOfHit; }
    public double expectedDamage() { return expectedDamage; }
}
