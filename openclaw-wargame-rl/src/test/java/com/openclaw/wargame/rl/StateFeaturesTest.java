package com.openclaw.wargame.rl;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateFeaturesTest {

    private BattleState buildStrongBlueWeakRed() {
        TerrainMap map = new TerrainMap(10000, 10000, 100);
        List<Unit> all = new ArrayList<>();
        // 5 个蓝方满血装甲
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100));
            all.add(u);
        }
        // 2 个红方残血装甲
        for (int i = 0; i < 2; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 2000 + i * 100));
            u.takeDamage(u.hp() * 0.8); // 残血
            all.add(u);
        }
        return new BattleState(0, 0, map, all);
    }

    private BattleState buildWeakBlueStrongRed() {
        TerrainMap map = new TerrainMap(10000, 10000, 100);
        List<Unit> all = new ArrayList<>();
        // 5 个蓝方，其中 4 个已死
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100));
            if (i > 0) u.takeDamage(u.hp() + 1); // 杀死
            else u.takeDamage(u.hp() * 0.9);
            all.add(u);
        }
        for (int i = 0; i < 5; i++) {
            all.add(Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 2000 + i * 100)));
        }
        return new BattleState(0, 0, map, all);
    }

    @Test
    void strongVsWeakHasHighFirepowerBucket() {
        StateFeatures sf = StateFeatures.extract(buildStrongBlueWeakRed(), Team.BLUE);
        // 火力优势 5 满血 vs 2 残血，应该是"压倒"
        assertEquals(3, sf.firepowerBucket(), "应该火力优势 3（压倒）");
        assertEquals(2, sf.aliveBucket(), "全部存活 → alive bucket 2");
    }

    @Test
    void weakVsStrongHasLowFirepowerBucket() {
        StateFeatures sf = StateFeatures.extract(buildWeakBlueStrongRed(), Team.BLUE);
        // 1 残血 vs 5 满血 → 火力劣势
        assertEquals(0, sf.firepowerBucket(), "应该火力劣势 bucket 0");
        assertEquals(0, sf.aliveBucket(), "己方存活少 → alive bucket 0");
    }

    @Test
    void encodeProducesDistinctValues() {
        StateFeatures a = new StateFeatures(0, 0, 0, 0, 0);
        StateFeatures b = new StateFeatures(0, 0, 0, 0, 1);
        StateFeatures c = new StateFeatures(3, 2, 2, 2, 1);
        assertNotEquals(a.encode(), b.encode(), "仅 lowHp 不同应编码不同");
        assertNotEquals(a.encode(), c.encode());
        assertNotEquals(b.encode(), c.encode());
    }

    @Test
    void encodeIsStable() {
        StateFeatures a = new StateFeatures(1, 2, 0, 2, 1);
        StateFeatures b = new StateFeatures(1, 2, 0, 2, 1);
        assertEquals(a.encode(), b.encode());
        assertEquals(a, b);
    }

    @Test
    void clampsOutOfRangeValues() {
        StateFeatures sf = new StateFeatures(99, -1, 0, 0, 0);
        assertEquals(StateFeatures.FIREPOWER_BUCKETS - 1, sf.firepowerBucket());
        assertEquals(0, sf.aliveBucket());
    }
}
