package com.openclaw.wargame.weapon;

import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.openclaw.wargame.realtime.HitEvent;
import com.openclaw.wargame.realtime.WeaponFiredEvent;
import com.openclaw.wargame.core.unit.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * 武器调度器：执行开火方案（应用概率伤害、扣除弹药、发布事件）。
 */
public final class WeaponScheduler {
    private static final Logger log = LoggerFactory.getLogger(WeaponScheduler.class);

    private final BattleEventBus bus;
    private final Random random;

    public WeaponScheduler(BattleEventBus bus, long seed) {
        this.bus = bus;
        this.random = new Random(seed);
    }

    /**
     * 执行一次开火回合：遍历 engagement，按概率结算命中。
     */
    public int fireOneRound(BattleState state, Team self, List<Engagement> engagements, long tick, double timeSeconds) {
        int hits = 0;
        for (Engagement e : engagements) {
            boolean fired = e.weapon().fire();
            if (!fired) continue;
            bus.publish(new WeaponFiredEvent(tick, timeSeconds,
                    e.shooter().id(), e.weapon().id(), e.weapon().type(),
                    e.target().id(), e.distance()));
            boolean hit = random.nextDouble() < e.probabilityOfHit();
            if (hit) {
                double actual = e.target().takeDamage(e.weapon().type().damage());
                boolean killed = !e.target().isAlive();
                bus.publish(new HitEvent(tick, timeSeconds,
                        e.shooter().id(), e.shooter().team(),
                        e.target().id(), e.target().team(),
                        actual, killed));
                hits++;
                if (killed) {
                    log.info("KILL: {} destroyed by {} via {}", e.target().id(), e.shooter().id(), e.weapon().type());
                }
            }
            e.weapon().onHit();
        }
        return hits;
    }
}
