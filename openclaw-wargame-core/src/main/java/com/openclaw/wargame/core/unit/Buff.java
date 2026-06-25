package com.openclaw.wargame.core.unit;

import java.util.Objects;
import java.util.UUID;

/**
 * 单位增益 Buff —— 临时修饰单位属性的能力。
 * <p>
 * 例如：
 * <ul>
 *   <li>火力强化：firepower + 30%（持续 10 tick）</li>
 *   <li>探测强化：detectionRange + 50%</li>
 *   <li>机动强化：speed + 40%</li>
 *   <li>装甲强化：armor + 50%</li>
 *   <li>伪装（潜行）：敌方更难探测到我</li>
 * </ul>
 * Buff 是 additive：多个 Buff 叠加效果会相加（设计简单）。
 */
public final class Buff {
    public enum Kind {
        FIREPOWER,    // 火力加成
        ARMOR,        // 装甲加成
        SPEED,        // 速度加成
        DETECTION,    // 探测范围加成
        STEALTH,      // 伪装（降低被探测概率）
        COORDINATION, // 协同（与友军协同加成）
        SUPPRESSION   // 压制（降低敌方命中率）
    }

    private final String id;
    private final Kind kind;
    private final double multiplier;  // 0.3 表示 +30%
    private int remainingTicks;       // 剩余 tick 数（可变）
    private final String source;      // 来源（哪个 advisor / commander）

    public Buff(Kind kind, double multiplier, int durationTicks, String source) {
        this.id = UUID.randomUUID().toString();
        this.kind = Objects.requireNonNull(kind);
        this.multiplier = multiplier;
        this.remainingTicks = durationTicks;
        this.source = source == null ? "system" : source;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public double multiplier() { return multiplier; }
    public int durationTicks() { return remainingTicks; }
    public String source() { return source; }

    /** 推进一个 tick，如果到期返回 false（应移除） */
    public boolean tick() {
        remainingTicks--;
        return remainingTicks > 0;
    }

    @Override
    public String toString() {
        return String.format("Buff[%s +%.0f%% %dt left by %s]", kind, multiplier * 100, remainingTicks, source);
    }
}
