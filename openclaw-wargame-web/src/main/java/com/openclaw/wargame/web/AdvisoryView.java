package com.openclaw.wargame.web;

import com.openclaw.wargame.analysis.BattleAdvantage;
import com.openclaw.wargame.analysis.TacticalAdvisor;
import com.openclaw.wargame.core.team.Team;

import java.util.List;

/**
 * 给 Web 展示用的"顾问报告"快照（每阵营一份）。
 */
public record AdvisoryView(
        Team team,
        BattleAdvantage advantage,
        List<TacticalAdvisor.Advice> advices,
        List<TacticalAdvisor.BuffAssignment> buffAssignments
) {}
