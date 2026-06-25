package com.openclaw.wargame.rl;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QLearnerTest {

    private BattleState buildState(Team perspective, int selfAlive, int enemyAlive) {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> all = new java.util.ArrayList<>();
        for (int i = 0; i < selfAlive; i++) {
            all.add(Unit.create(UnitType.ARMOR, perspective, new Position(100 + i * 100, 100)));
        }
        Team enemy = perspective == Team.BLUE ? Team.RED : Team.BLUE;
        for (int i = 0; i < enemyAlive; i++) {
            all.add(Unit.create(UnitType.ARMOR, enemy, new Position(2500 + i * 100, 2500)));
        }
        return new BattleState(0, 0, map, all);
    }

    @Test
    void initialEpsilonHigh() {
        QLearner ql = new QLearner(42);
        assertTrue(ql.epsilon() > 0.2, "初始 ε 应该较大以鼓励探索");
    }

    @Test
    void epsilonDecays() {
        QLearner ql = new QLearner(42);
        double before = ql.epsilon();
        for (int i = 0; i < 10; i++) ql.endEpisode();
        assertTrue(ql.epsilon() < before, "ε 应该随 episode 衰减");
        assertTrue(ql.epsilon() >= QLearner.MIN_EPSILON, "ε 不应低于 MIN_EPSILON");
    }

    @Test
    void selectActionValidStrategy() {
        QLearner ql = new QLearner(42);
        BattleState s = buildState(Team.BLUE, 5, 5);
        StateFeatures sf = StateFeatures.extract(s, Team.BLUE);
        for (int i = 0; i < 100; i++) {
            Strategy a = ql.selectAction(sf);
            assertNotNull(a);
            assertTrue(a.index() >= 0 && a.index() < Strategy.size());
        }
    }

    @Test
    void updateModifiesQTable() {
        QLearner ql = new QLearner(42);
        BattleState s1 = buildState(Team.BLUE, 5, 5);
        BattleState s2 = buildState(Team.BLUE, 4, 3); // 己方少 1，敌方少 2
        StateFeatures sf1 = StateFeatures.extract(s1, Team.BLUE);
        StateFeatures sf2 = StateFeatures.extract(s2, Team.BLUE);
        ql.update(sf1, Strategy.AGGRESSIVE, 25.0, sf2, false);
        assertTrue(ql.qValue(sf1, Strategy.AGGRESSIVE) > 0, "Q 值应该被更新为正");
        assertTrue(ql.updateCount() > 0);
    }

    @Test
    void bestActionIsGreedy() {
        QLearner ql = new QLearner(42);
        BattleState s = buildState(Team.BLUE, 5, 5);
        StateFeatures sf = StateFeatures.extract(s, Team.BLUE);
        // 给某个动作很大的 Q 值
        for (int i = 0; i < 100; i++) {
            ql.update(sf, Strategy.DEFENSIVE, 100, sf, true);
        }
        // 现在 bestAction 应该总是选 DEFENSIVE
        for (int i = 0; i < 10; i++) {
            assertEquals(Strategy.DEFENSIVE, ql.bestAction(sf));
        }
    }

    @Test
    void selectActionExploresSometimes() {
        // ε=1 时应该完全随机
        QLearner ql = new QLearner(0.1, 0.95, 1.0, 0.995, 42);
        BattleState s = buildState(Team.BLUE, 5, 5);
        StateFeatures sf = StateFeatures.extract(s, Team.BLUE);
        java.util.Set<Strategy> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(ql.selectAction(sf));
        }
        // ε=1 时应该看到多个动作
        assertTrue(seen.size() >= 3, "ε=1 时应该探索多种动作，看到 " + seen.size());
    }

    @Test
    void learningImprovesReward() {
        // 模拟一个简单的学习过程：每次都收到正奖励给 AGGRESSIVE
        QLearner ql = new QLearner(0.5, 0.9, 0.5, 0.99, 42);
        BattleState s = buildState(Team.BLUE, 5, 5);
        StateFeatures sf = StateFeatures.extract(s, Team.BLUE);
        double q0 = ql.qValue(sf, Strategy.AGGRESSIVE);
        for (int i = 0; i < 50; i++) {
            ql.update(sf, Strategy.AGGRESSIVE, 10.0, sf, true);
            ql.endEpisode();
        }
        double q1 = ql.qValue(sf, Strategy.AGGRESSIVE);
        assertTrue(q1 > q0, "学习后 Q 值应该上升 q0=" + q0 + " q1=" + q1);
        assertEquals(Strategy.AGGRESSIVE, ql.bestAction(sf));
    }
}
