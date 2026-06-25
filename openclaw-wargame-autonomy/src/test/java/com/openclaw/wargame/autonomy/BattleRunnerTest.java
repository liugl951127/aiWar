package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import com.openclaw.wargame.realtime.BattleClock;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.openclaw.wargame.simulation.Simulator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleRunnerTest {

    private BattleState buildInitial() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> blue = new ArrayList<>();
        Unit bCmd = Unit.create(UnitType.COMMAND, Team.BLUE, new Position(100, 100));
        blue.add(bCmd);
        for (int i = 0; i < 3; i++) {
            Unit a = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(200 + i * 100, 100));
            a.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            blue.add(a);
        }
        List<Unit> red = new ArrayList<>();
        Unit rCmd = Unit.create(UnitType.COMMAND, Team.RED, new Position(1000, 100));
        red.add(rCmd);
        for (int i = 0; i < 3; i++) {
            Unit a = Unit.create(UnitType.ARMOR, Team.RED, new Position(1100, 100 + i * 100));
            a.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            red.add(a);
        }
        return Simulator.createInitial(map, blue, red, List.of());
    }

    @Test
    void runnersCompleteAndConverge() {
        BattleState initial = buildInitial();
        Simulator sim = new Simulator(5.0, 1);
        BattleClock clock = new BattleClock(5.0);
        BattleEventBus bus = new BattleEventBus(256, BattleEventBus.BackpressurePolicy.BLOCK);
        AutonomyLoop blue = AutonomyLoop.create(Team.BLUE, sim, clock, bus, 1);
        AutonomyLoop red = AutonomyLoop.create(Team.RED, sim, clock, bus, 2);
        BattleRunner runner = new BattleRunner(sim, clock, bus, blue, red, 50, null);
        BattleState end = runner.run(initial);
        // 一段时间后状态有推进
        assertTrue(end.tick() > 0);
        // 至少有一方做出了决策
        assertNotNull(blue.lastPlan());
        assertNotNull(red.lastPlan());
    }

    @Test
    void engagementProducesEvents() {
        BattleState initial = buildInitial();
        Simulator sim = new Simulator(5.0, 7);
        BattleClock clock = new BattleClock(5.0);
        BattleEventBus bus = new BattleEventBus(1024, BattleEventBus.BackpressurePolicy.BLOCK);
        AutonomyLoop blue = AutonomyLoop.create(Team.BLUE, sim, clock, bus, 7);
        AutonomyLoop red = AutonomyLoop.create(Team.RED, sim, clock, bus, 8);
        BattleRunner runner = new BattleRunner(sim, clock, bus, blue, red, 30, null);
        BattleState end = runner.run(initial);
        assertTrue(bus.publishedCount() > 0, "应该发布过事件");
        // 至少有一方开过火（WeaponFired 或 Hit）
        boolean hadFire = end.units().stream().anyMatch(u -> u.weapons().stream().anyMatch(w -> w.ammo() < w.maxAmmo()));
        assertTrue(hadFire, "至少有一个武器消耗过弹药");
    }
}
