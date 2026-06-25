package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SituationalAnalysisTest {

    private BattleState buildState() {
        TerrainMap map = new TerrainMap(1000, 1000, 50);
        Unit blue1 = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        Unit blue2 = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(150, 100));
        Unit red1 = Unit.create(UnitType.ARMOR, Team.RED, new Position(900, 900));
        Unit red2 = Unit.create(UnitType.ARMOR, Team.RED, new Position(950, 950));
        return new BattleState(0, 0, map, List.of(blue1, blue2, red1, red2));
    }

    @Test
    void topThreatsRanksByScore() {
        BattleState s = buildState();
        SituationalAnalysis sa = new SituationalAnalysis(s, Team.BLUE);
        var threats = sa.topThreats(2);
        assertEquals(2, threats.size());
        // 第一个是更高威胁（更近 + 满血）
        assertTrue(threats.get(0).threatScore() >= threats.get(1).threatScore());
    }

    @Test
    void firepowerRatioBalanced() {
        BattleState s = buildState();
        SituationalAnalysis sa = new SituationalAnalysis(s, Team.BLUE);
        double ratio = sa.firepowerRatio();
        // 双方都是 2 辆满血装甲，比值约 1.0
        assertTrue(ratio > 0.8 && ratio < 1.3, "ratio=" + ratio);
    }

    @Test
    void weakestQuadrantDetected() {
        BattleState s = buildState();
        // 蓝方全部在 SW（西南）
        SituationalAnalysis sa = new SituationalAnalysis(s, Team.BLUE);
        var weakest = sa.weakestQuadrant();
        assertEquals(SituationalAnalysis.Quadrant.NE, weakest.quadrant());
        assertTrue(weakest.isGap(1));
    }

    @Test
    void nearestEnemy() {
        BattleState s = buildState();
        SituationalAnalysis sa = new SituationalAnalysis(s, Team.BLUE);
        Unit nearest = sa.nearestEnemy();
        assertNotNull(nearest);
        assertEquals(Team.RED, nearest.team());
    }
}
