package com.openclaw.wargame.core.unit;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnitTest {

    @Test
    void createWithFullHp() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        assertTrue(u.isAlive());
        assertEquals(1.0, u.hpRatio(), 1e-9);
        assertEquals(UnitStatus.IDLE, u.status());
    }

    @Test
    void takeDamageReducesHp() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        double before = u.hp();
        double taken = u.takeDamage(3);
        assertEquals(3.0, taken, 1e-9);
        assertEquals(before - 3, u.hp(), 1e-9);
    }

    @Test
    void fatalDamageMarksDestroyed() {
        Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(0, 0));
        u.takeDamage(100);
        assertFalse(u.isAlive());
        assertEquals(UnitStatus.DESTROYED, u.status());
    }

    @Test
    void mountWeaponUpToLimit() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 10));
        u.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 100));
        assertThrows(IllegalStateException.class, () -> u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 10)));
    }

    @Test
    void selectWeaponConsidersRange() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        Weapon tank = new Weapon(WeaponType.TANK_CANNON, 10);
        Weapon mg = new Weapon(WeaponType.MACHINE_GUN, 100);
        u.mountWeapon(tank);
        u.mountWeapon(mg);
        // 1000m: tank 优先（伤害更高，射程覆盖）
        Weapon w = u.selectWeaponFor(1000);
        assertEquals(WeaponType.TANK_CANNON, w.type());
    }

    @Test
    void selectWeaponOutOfRangeReturnsNull() {
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(0, 0));
        u.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 100));
        // 10000m 超出机炮射程
        assertNull(u.selectWeaponFor(10000));
    }

    @Test
    void setMoveTargetChangesStatus() {
        Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(0, 0));
        u.setMoveTarget(new Position(100, 0));
        assertEquals(UnitStatus.MOVING, u.status());
        u.clearMoveTarget();
        assertEquals(UnitStatus.IDLE, u.status());
    }
}
