package com.openclaw.wargame.ai.rules;

import com.openclaw.wargame.analysis.SituationalAnalysis;
import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.core.state.BattleState;

import java.util.List;

/**
 * 决策规则接口：输入态势，输出 0..N 个候选动作。
 */
public interface Rule {
    /** 规则名 */
    String name();

    /** 规则适用阵营（null 表示通用） */
    default com.openclaw.wargame.core.team.Team applicableTeam() { return null; }

    /** 评估态势并产生动作 */
    List<Action> evaluate(BattleState state, SituationalAnalysis analysis);
}
