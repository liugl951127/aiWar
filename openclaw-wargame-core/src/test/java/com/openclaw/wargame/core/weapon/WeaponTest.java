package com.openclaw.wargame.core.weapon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponTest {

    @Test
    void initialFullAmmo() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 100);
        assertEquals(100, w.ammo());
        assertTrue(w.canFire());
        assertFalse(w.inFlight());
    }

    @Test
    void fireDecrementsAmmo() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 100);
        assertTrue(w.fire());
        assertEquals(99, w.ammo());
        assertTrue(w.inFlight());
    }

    @Test
    void cannotFireWhenEmpty() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 1);
        w.fire();
        w.onHit();
        assertFalse(w.fire());
    }

    @Test
    void cannotFireWhileInFlight() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 10);
        w.fire();
        assertFalse(w.fire());
        w.onHit();
        assertTrue(w.fire());
    }

    @Test
    void reloadRestoresAmmo() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 10);
        w.fire();
        w.onHit();
        w.fire();
        w.onHit();
        assertEquals(8, w.ammo());
        w.reload();
        assertEquals(10, w.ammo());
    }

    @Test
    void resupply() {
        Weapon w = new Weapon(WeaponType.MACHINE_GUN, 10);
        for (int i = 0; i < 10; i++) { w.fire(); w.onHit(); }
        assertEquals(0, w.ammo());
        w.resupply(5);
        assertEquals(5, w.ammo());
        w.resupply(100);
        assertEquals(10, w.ammo()); // 不超 max
    }
}
