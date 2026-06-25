package com.openclaw.wargame.core.unit;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.team.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuffTest {

    @Test
    void effectiveFirepowerScalesWithBuff() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double baseFp = u.effectiveFirepower();
        u.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.3, 5, "test"));
        assertEquals(baseFp * 1.3, u.effectiveFirepower(), 1e-9);
    }

    @Test
    void effectiveSpeedScalesWithBuff() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double base = u.effectiveSpeed();
        u.applyBuff(new Buff(Buff.Kind.SPEED, 0.5, 5, "test"));
        assertEquals(base * 1.5, u.effectiveSpeed(), 1e-9);
    }

    @Test
    void multipleBuffsStack() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double base = u.effectiveFirepower();
        u.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.3, 5, "test1"));
        u.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.2, 5, "test2"));
        assertEquals(base * 1.5, u.effectiveFirepower(), 1e-9);
    }

    @Test
    void buffDecaysAndIsRemoved() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double baseFp = u.effectiveFirepower();
        u.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.5, 3, "test"));
        // tick 一次：剩余 2 tick
        u.tickBuffs();
        assertEquals(1, u.buffs().size(), "buff 还在");
        // 再 tick 一次：剩余 1 tick（>0）
        u.tickBuffs();
        assertEquals(1, u.buffs().size());
        // 再 tick 一次：剩余 0 → 移除
        u.tickBuffs();
        assertEquals(0, u.buffs().size(), "buff 应该被移除");
        assertEquals(baseFp, u.effectiveFirepower(), 1e-9);
    }

    @Test
    void armorBuffScalesMaxHp() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double baseMax = u.maxHp();
        u.applyBuff(new Buff(Buff.Kind.ARMOR, 0.5, 5, "test"));
        assertEquals(baseMax * 1.5, u.maxHp(), 1e-9);
    }

    @Test
    void stealthAndSuppressionAreExposed() {
        Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(0, 0));
        u.applyBuff(new Buff(Buff.Kind.STEALTH, 0.4, 5, "test"));
        u.applyBuff(new Buff(Buff.Kind.SUPPRESSION, 0.3, 5, "test"));
        assertEquals(0.4, u.stealthFactor(), 1e-9);
        assertEquals(0.3, u.suppressionFactor(), 1e-9);
    }

    @Test
    void detectionBuffIncreasesRange() {
        Unit u = Unit.create(UnitType.RECON, Team.BLUE, new Position(0, 0));
        double base = u.effectiveDetectionRange();
        u.applyBuff(new Buff(Buff.Kind.DETECTION, 0.5, 5, "test"));
        assertEquals(base * 1.5, u.effectiveDetectionRange(), 1e-9);
    }

    @Test
    void buffsListIsUnmodifiable() {
        Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(0, 0));
        u.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.3, 5, "test"));
        assertThrows(UnsupportedOperationException.class, () -> u.buffs().add(new Buff(Buff.Kind.SPEED, 0.1, 5, "x")));
    }
}
