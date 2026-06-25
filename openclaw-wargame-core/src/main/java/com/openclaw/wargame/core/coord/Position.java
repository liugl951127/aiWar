package com.openclaw.wargame.core.coord;

import java.util.Objects;

/**
 * 2D 战场坐标 (x, y)。x 为经度方向，y 为纬度方向，单位: 米。
 * <p>
 * Position 是不可变值对象。所有移动/交战计算都基于 Position 的距离与方向。
 */
public final class Position {
    private final double x;
    private final double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /**
     * 计算到目标位置的欧氏距离（米）。
     */
    public double distanceTo(Position other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 从当前点向目标点方向按 distance 米移动，返回新 Position。
     * distance 为负表示背离方向。
     */
    public Position moveTowards(Position target, double distance) {
        double total = distanceTo(target);
        if (total <= 1e-9) {
            return this;
        }
        double ratio = distance / total;
        if (ratio > 1.0) ratio = 1.0;
        if (ratio < -1.0) ratio = -1.0;
        return new Position(
                this.x + (target.x - this.x) * ratio,
                this.y + (target.y - this.y) * ratio
        );
    }

    /**
     * 计算从当前点指向 target 的方位角（弧度，0 表示 +x 轴方向，逆时针为正）。
     */
    public double bearingTo(Position target) {
        double dx = target.x - this.x;
        double dy = target.y - this.y;
        return Math.atan2(dy, dx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position p)) return false;
        return Double.compare(p.x, x) == 0 && Double.compare(p.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }

    /** 限制到 [0, max] 矩形内 */
    public Position clamp(double maxX, double maxY) {
        return new Position(
                Math.max(0, Math.min(maxX, x)),
                Math.max(0, Math.min(maxY, y))
        );
    }
}
