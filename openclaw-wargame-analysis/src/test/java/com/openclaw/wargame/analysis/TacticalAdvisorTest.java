package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TacticalAdvisorTest {

    private BattleState buildStrongVsWeak() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> all = new ArrayList<>();
        // 5 蓝方满血
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100));
            u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            all.add(u);
        }
        // 1 红方
        Unit r = Unit.create(UnitType.ARMOR, Team.RED, new Position(2500, 2500));
        r.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
        all.add(r);
        return new BattleState(0, 0, map, all);
    }

    private BattleState buildWeakVsStrong() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> all = new ArrayList<>();
        // 1 蓝方残血
        Unit b = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        b.takeDamage(b.hp() * 0.8);
        b.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
        all.add(b);
        // 5 红方
        for (int i = 0; i < 5; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.RED, new Position(2500, 100 + i * 100));
            u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            all.add(u);
        }
        return new BattleState(0, 0, map, all);
    }

    @Test
    void strongBlueGetsAttackAdvice() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TacticalAdvisor.AdvisoryReport report = adv.advise(buildStrongVsWeak());
        boolean hasAttack = report.advices().stream()
                .anyMatch(a -> a.kind() == TacticalAdvisor.AdviceKind.ATTACK
                        || a.kind() == TacticalAdvisor.AdviceKind.COORDINATE_FIRE);
        assertTrue(hasAttack, "蓝方优势时应该有 ATTACK 或 COORDINATE_FIRE 建议");
    }

    @Test
    void weakBlueGetsDefendAdvice() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TacticalAdvisor.AdvisoryReport report = adv.advise(buildWeakVsStrong());
        boolean hasDefend = report.advices().stream()
                .anyMatch(a -> a.kind() == TacticalAdvisor.AdviceKind.DEFEND
                        || a.kind() == TacticalAdvisor.AdviceKind.EMERGENCY_RETREAT);
        assertTrue(hasDefend, "蓝方劣势时应该有 DEFEND 或 EMERGENCY_RETREAT 建议");
    }

    @Test
    void firepowerBuffAssignedForStrongUnits() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TacticalAdvisor.AdvisoryReport report = adv.advise(buildStrongVsWeak());
        // 蓝方优势时应该给高血量单位加火力 buff
        boolean hasFpBuff = report.buffAssignments().stream()
                .anyMatch(b -> b.buffKind() == com.openclaw.wargame.core.unit.Buff.Kind.FIREPOWER);
        assertTrue(hasFpBuff, "攻击建议时应该产出 FIREPOWER Buff");
    }

    @Test
    void armorBuffForDefense() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TacticalAdvisor.AdvisoryReport report = adv.advise(buildWeakVsStrong());
        boolean hasArmor = report.buffAssignments().stream()
                .anyMatch(b -> b.buffKind() == com.openclaw.wargame.core.unit.Buff.Kind.ARMOR);
        assertTrue(hasArmor, "防御建议时应该产出 ARMOR Buff");
    }

    @Test
    void applyBuffsActuallyApplies() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        BattleState state = buildStrongVsWeak();
        TacticalAdvisor.AdvisoryReport report = adv.advise(state);
        int applied = TacticalAdvisor.applyBuffs(state, report.buffAssignments());
        assertTrue(applied > 0, "应该成功应用一些 Buff: " + applied);
        // 验证至少一个 unit 有 buff
        boolean anyBuff = state.units().stream().anyMatch(u -> !u.buffs().isEmpty());
        assertTrue(anyBuff);
    }

    @Test
    void advantageSummaryIncluded() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TacticalAdvisor.AdvisoryReport report = adv.advise(buildStrongVsWeak());
        assertNotNull(report.advantage());
        assertTrue(report.advantage().overall() > 0.5, "强 vs 弱应该 overall > 0.5");
    }

    @Test
    void emptyStateNoCrash() {
        TacticalAdvisor adv = new TacticalAdvisor(Team.BLUE);
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        BattleState empty = new BattleState(0, 0, map, new ArrayList<>());
        TacticalAdvisor.AdvisoryReport report = adv.advise(empty);
        assertNotNull(report);
    }
}
