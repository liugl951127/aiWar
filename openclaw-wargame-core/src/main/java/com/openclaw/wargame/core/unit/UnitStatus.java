package com.openclaw.wargame.core.unit;

/**
 * 单位状态机。
 * <pre>
 *   IDLE ──setMoveTarget──► MOVING ──arrive──► IDLE
 *     │                       │
 *     ├─enterEngagement──► ENGAGING ──out of ammo/ammo────► IDLE
 *     │                       │
 *     ├─retreat──────────► RETREATING ──safe──► IDLE
 *     │
 *     └─takeDamage──► hp<=0 ──► DESTROYED
 * </pre>
 */
public enum UnitStatus {
    IDLE,
    MOVING,
    ENGAGING,
    RETREATING,
    DESTROYED
}
