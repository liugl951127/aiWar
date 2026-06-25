package com.openclaw.wargame.weapon;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FireControlComputerTest {

    private BattleState buildState() {
        TerrainMap map = new TerrainMap(10000, 10000, 100);
        Unit blue = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        blue.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
        blue.mountWeapon(new Weapon(WeaponType.MACHINE_GUN, 200));
        Unit red = Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 100));
        return new BattleState(0, 0, map, List.of(blue, red));
    }

    @Test
    void computesEngagements() {
        BattleState s = buildState();
        FireControlComputer fcc = new FireControlComputer(new RulesOfEngagement());
        List<Engagement> list = fcc.compute(s, Team.BLUE);
        assertEquals(1, list.size());
        Engagement e = list.get(0);
        assertEquals(1900, e.distance(), 1);
        assertTrue(e.probabilityOfHit() > 0 && e.probabilityOfHit() < 1);
    }

    @Test
    void accuracyIsCapped() {
        BattleState s = buildState();
        FireControlComputer fcc = new FireControlComputer(new RulesOfEngagement());
        Engagement e = fcc.compute(s, Team.BLUE).get(0);
        assertTrue(e.probabilityOfHit() <= 0.99);
    }

    @Test
    void roeHoldBlocksFire() {
        BattleState s = buildState();
        RulesOfEngagement roe = new RulesOfEngagement().setMode(RulesOfEngagement.Mode.WEAPONS_HOLD);
        FireControlComputer fcc = new FireControlComputer(roe);
        List<Engagement> list = fcc.compute(s, Team.BLUE);
        assertTrue(list.isEmpty());
    }

    @Test
    void roeTightOnlyOnFire() {
        BattleState s = buildState();
        RulesOfEngagement roe = new RulesOfEngagement().setMode(RulesOfEngagement.Mode.WEAPONS_TIGHT);
        FireControlComputer fcc = new FireControlComputer(roe);
        List<Engagement> list = fcc.compute(s, Team.BLUE);
        assertTrue(list.isEmpty());
    }
}
