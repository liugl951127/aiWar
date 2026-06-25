package com.openclaw.wargame.analysis;

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

class BattleAdvantageTest {

    private BattleState buildState(double blueFirepower, double redFirepower) {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> all = new ArrayList<>();
        // 简单按火力数加蓝方
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100));
            all.add(u);
        }
        // 红方按 redFirepower 调整
        int redCount = Math.max(1, (int) (redFirepower * 5));
        for (int i = 0; i < redCount; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.RED, new Position(2500, 100 + i * 100));
            all.add(u);
        }
        return new BattleState(0, 0, map, all);
    }

    @Test
    void blueDominantWhenMoreFirepower() {
        BattleAdvantage adv = BattleAdvantage.compute(buildState(5, 1), Team.BLUE);
        assertTrue(adv.overall() > 0.55, "蓝方火力优势压倒时 overall > 0.55: " + adv.overall());
    }

    @Test
    void balancedWhenEqual() {
        BattleAdvantage adv = BattleAdvantage.compute(buildState(5, 5), Team.BLUE);
        assertEquals(0.5, adv.overall(), 0.1, "双方对等时 overall ≈ 0.5: " + adv.overall());
    }

    @Test
    void fieldsAreBoundedZeroOne() {
        BattleAdvantage adv = BattleAdvantage.compute(buildState(3, 3), Team.BLUE);
        for (double v : new double[]{adv.firepower(), adv.manpower(), adv.detection(), adv.mobility(), adv.cohesion(), adv.overall()}) {
            assertTrue(v >= 0 && v <= 1, "field out of range: " + v);
        }
    }

    @Test
    void isDominantThreshold() {
        BattleAdvantage adv = BattleAdvantage.compute(buildState(5, 1), Team.BLUE);
        assertTrue(adv.isDominant(0.5));
        assertFalse(adv.isWeaker(0.5));
    }

    @Test
    void emptyStateNoCrash() {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        BattleState empty = new BattleState(0, 0, map, new ArrayList<>());
        BattleAdvantage adv = BattleAdvantage.compute(empty, Team.BLUE);
        assertEquals(0.5, adv.overall(), 0.01);
    }
}
