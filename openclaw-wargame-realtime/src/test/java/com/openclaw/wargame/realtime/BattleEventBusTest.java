package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BattleEventBusTest {

    @Test
    void publishAndReceive() {
        BattleEventBus bus = new BattleEventBus(8, BattleEventBus.BackpressurePolicy.BLOCK);
        List<BattleEvent> received = new ArrayList<>();
        bus.subscribe(received::add);
        bus.publish(new DetectionEvent(1, 5.0, "d1", Team.BLUE, "t1", Team.RED, null, 1000));
        bus.publish(new DetectionEvent(2, 10.0, "d2", Team.BLUE, "t2", Team.RED, null, 2000));
        assertEquals(2, received.size());
        assertEquals(2, bus.publishedCount());
    }

    @Test
    void recentSnapshot() {
        BattleEventBus bus = new BattleEventBus(16, BattleEventBus.BackpressurePolicy.BLOCK);
        for (int i = 0; i < 5; i++) {
            bus.publish(new DetectionEvent(i, i * 1.0, "d", Team.BLUE, "t", Team.RED, null, 100));
        }
        var recent = bus.recent(3);
        assertEquals(3, recent.size());
    }

    @Test
    void dropPolicyDropsOldest() {
        BattleEventBus bus = new BattleEventBus(4, BattleEventBus.BackpressurePolicy.DROP);
        for (int i = 0; i < 10; i++) {
            bus.publish(new DetectionEvent(i, i, "d", Team.BLUE, "t", Team.RED, null, 1));
        }
        assertTrue(bus.droppedCount() > 0);
        assertEquals(10, bus.publishedCount());
    }

    @Test
    void concurrentPublishersAreSafe() throws InterruptedException {
        BattleEventBus bus = new BattleEventBus(1024, BattleEventBus.BackpressurePolicy.BLOCK);
        ExecutorService exec = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);
        int perThread = 250;
        for (int t = 0; t < 4; t++) {
            exec.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        bus.publish(new DetectionEvent(0, 0, "d", Team.BLUE, "t", Team.RED, null, 1));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(4 * perThread, bus.publishedCount());
    }
}
