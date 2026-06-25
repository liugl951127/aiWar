package com.openclaw.wargame.ai.decision;

import com.openclaw.wargame.core.coord.Position;

import java.util.UUID;

/**
 * 决策动作：单位可以执行的行为。
 */
public abstract class Action {
    private final String id = UUID.randomUUID().toString();
    private final String unitId;
    private final double priority;

    protected Action(String unitId, double priority) {
        this.unitId = unitId;
        this.priority = priority;
    }

    public String id() { return id; }
    public String unitId() { return unitId; }
    public double priority() { return priority; }

    public abstract ActionKind kind();

    public enum ActionKind {
        MOVE,           // 移动
        ENGAGE,         // 攻击指定目标
        DEFEND,         // 防御/占领
        RETREAT,        // 撤退
        RESUPPLY,       // 补给
        RECON,          // 侦察
        COORDINATE_FIRE // 火力协同
    }

    public static final class MoveAction extends Action {
        private final Position destination;

        public MoveAction(String unitId, Position destination, double priority) {
            super(unitId, priority);
            this.destination = destination;
        }

        public Position destination() { return destination; }

        @Override public ActionKind kind() { return ActionKind.MOVE; }
    }

    public static final class EngageAction extends Action {
        private final String targetId;

        public EngageAction(String unitId, String targetId, double priority) {
            super(unitId, priority);
            this.targetId = targetId;
        }

        public String targetId() { return targetId; }

        @Override public ActionKind kind() { return ActionKind.ENGAGE; }
    }

    public static final class RetreatAction extends Action {
        private final Position safePosition;

        public RetreatAction(String unitId, Position safePosition, double priority) {
            super(unitId, priority);
            this.safePosition = safePosition;
        }

        public Position safePosition() { return safePosition; }

        @Override public ActionKind kind() { return ActionKind.RETREAT; }
    }

    public static final class DefendAction extends Action {
        private final Position defendPosition;

        public DefendAction(String unitId, Position defendPosition, double priority) {
            super(unitId, priority);
            this.defendPosition = defendPosition;
        }

        public Position defendPosition() { return defendPosition; }

        @Override public ActionKind kind() { return ActionKind.DEFEND; }
    }
}
