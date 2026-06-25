package com.openclaw.wargame.ai.rules;

import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private BattleState simpleState() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        Unit cmd = Unit.create(UnitType.COMMAND, Team.BLUE, new Position(100, 100));
        // 使用 RECON, detection=6, 可以 attack 6*500=3000m
        Unit recon1 = Unit.create(UnitType.RECON, Team.BLUE, new Position(200, 100));
        Unit recon2 = Unit.create(UnitType.RECON, Team.BLUE, new Position(300, 100));
        Unit recon3 = Unit.create(UnitType.RECON, Team.BLUE, new Position(400, 100));
        // 把红方放在 RECON 探测范围内
        Unit redArmor = Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 100));
        Unit redArmor2 = Unit.create(UnitType.ARMOR, Team.RED, new Position(2100, 100));
        return new BattleState(0, 0, map, List.of(cmd, recon1, recon2, recon3, redArmor, redArmor2));
    }

    @Test
    void defaultRulesProducePlan() {
        BattleState s = simpleState();
        RuleEngine engine = DefaultRules.createDefault();
        DecisionPlan plan = engine.decide(s, Team.BLUE);
        assertNotNull(plan);
        assertEquals(Team.BLUE, plan.team());
        assertFalse(plan.actions().isEmpty(), "应该有动作产生");
    }

    @Test
    void eachUnitHasAtMostOneAction() {
        BattleState s = simpleState();
        RuleEngine engine = DefaultRules.createDefault();
        DecisionPlan plan = engine.decide(s, Team.BLUE);
        // 单位去重
        java.util.Set<String> units = new java.util.HashSet<>();
        for (Action a : plan.actions()) {
            assertTrue(units.add(a.unitId()), "重复动作单位: " + a.unitId());
        }
    }

    @Test
    void engageThreatsProducesEngagement() {
        BattleState s = simpleState();
        RuleEngine engine = DefaultRules.createDefault();
        DecisionPlan plan = engine.decide(s, Team.BLUE);
        boolean hasEngage = false;
        for (Action a : plan.actions()) {
            if (a instanceof Action.EngageAction) { hasEngage = true; break; }
        }
        assertTrue(hasEngage, "EngageThreatsRule should produce EngageAction");
    }

    @Test
    void planSummaryNotEmpty() {
        BattleState s = simpleState();
        RuleEngine engine = DefaultRules.createDefault();
        DecisionPlan plan = engine.decide(s, Team.BLUE);
        assertNotNull(plan.summary());
        assertFalse(plan.summary().isEmpty());
    }
}
