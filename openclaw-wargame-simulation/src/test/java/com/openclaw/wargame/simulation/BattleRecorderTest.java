package com.openclaw.wargame.simulation;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Buff;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleRecorderTest {

    @Test
    void recordAndReplay(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("battle.jsonl");

        // 录制
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        Unit u1 = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        u1.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
        u1.applyBuff(new Buff(Buff.Kind.FIREPOWER, 0.3, 5, "test"));
        Unit u2 = Unit.create(UnitType.ARMOR, Team.RED, new Position(2500, 2500));
        u2.takeDamage(2);

        BattleState s0 = new BattleState(0, 0, map, List.of(u1, u2));
        BattleState s1 = new BattleState(5, 25.0, map, List.of(u1, u2));

        try (BattleRecorder rec = new BattleRecorder(file)) {
            rec.writeHeader(s0);
            rec.recordTick(s1);
            rec.writeEnd("RED", 5);
        }

        // 回放
        List<BattleState> replayed = BattleRecorder.readAll(file);
        assertEquals(1, replayed.size(), "应该录到 1 个 tick");
        BattleState r1 = replayed.get(0);
        assertEquals(5, r1.tick());
        assertEquals(25.0, r1.timeSeconds(), 1e-9);
        assertEquals(2, r1.units().size());
        assertEquals(Team.BLUE, r1.units().get(0).team());
        assertEquals(UnitType.ARMOR, r1.units().get(0).type());
        assertEquals(100.0, r1.units().get(0).position().x(), 1e-9);
        assertEquals(2500.0, r1.units().get(1).position().x(), 1e-9);
        // hp 不一样（红方被打掉 2）
        assertEquals(u2.maxHp() - 2, r1.units().get(1).hp(), 1e-9);
        // buff 还原
        assertEquals(1, r1.units().get(0).buffs().size());
        assertEquals(Buff.Kind.FIREPOWER, r1.units().get(0).buffs().get(0).kind());
    }

    @Test
    void recordMultipleTicks(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("multi.jsonl");
        TerrainMap map = new TerrainMap(2000, 2000, 100);
        Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(500, 500));
        try (BattleRecorder rec = new BattleRecorder(file)) {
            BattleState s0 = new BattleState(0, 0, map, List.of(u));
            rec.writeHeader(s0);
            for (int i = 1; i <= 10; i++) {
                rec.recordTick(new BattleState(i, i * 5.0, map, List.of(u)));
            }
            rec.writeEnd(null, 10);
        }
        List<BattleState> out = BattleRecorder.readAll(file);
        assertEquals(10, out.size());
        assertEquals(1, out.get(0).tick());
        assertEquals(10, out.get(9).tick());
        assertEquals(50.0, out.get(9).timeSeconds(), 1e-9);
    }

    @Test
    void emptyRecording(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("empty.jsonl");
        try (BattleRecorder rec = new BattleRecorder(file)) {
            // 不写任何 tick
        }
        List<BattleState> out = BattleRecorder.readAll(file);
        assertTrue(out.isEmpty());
    }

    @Test
    void mustWriteHeaderFirst(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("bad.jsonl");
        try (BattleRecorder rec = new BattleRecorder(file)) {
            TerrainMap map = new TerrainMap(1000, 1000, 100);
            Unit u = Unit.create(UnitType.INFANTRY, Team.BLUE, new Position(0, 0));
            assertThrows(IllegalStateException.class,
                    () -> rec.recordTick(new BattleState(1, 5.0, map, List.of(u))));
        }
    }
}