package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 战场事件总线（高吞吐事件流）。
 * <p>
 * 内部用一个环形缓冲（数组 + producerIndex/consumerIndex）实现单生产者多消费者的无锁队列：
 * <ul>
 *   <li>主仿真循环是唯一生产者（publish）</li>
 *   <li>分析、决策、武器调度、日志模块各自作为消费者订阅</li>
 * </ul>
 * 当缓冲满时，生产者会阻塞或丢弃（取决于 BackpressurePolicy）。
 */
public final class BattleEventBus {
    private static final Logger log = LoggerFactory.getLogger(BattleEventBus.class);

    public enum BackpressurePolicy {
        BLOCK,   // 阻塞生产者
        DROP     // 丢弃最旧事件
    }

    private final int capacity;
    private final BattleEvent[] buffer;
    private final BackpressurePolicy policy;
    private final AtomicLong producerIndex = new AtomicLong(0);
    private final AtomicLong consumerIndex = new AtomicLong(0);
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong publishedCount = new AtomicLong(0);

    public BattleEventBus(int capacity, BackpressurePolicy policy) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("capacity must be power of two");
        }
        this.capacity = capacity;
        this.buffer = new BattleEvent[capacity];
        this.policy = policy;
    }

    public int capacity() { return capacity; }
    public long publishedCount() { return publishedCount.get(); }
    public long droppedCount() { return droppedCount.get(); }

    public void subscribe(Subscriber s) {
        subscribers.add(s);
    }

    /**
     * 发布事件（线程安全）。
     */
    public void publish(BattleEvent event) {
        long pIdx, cIdx;
        boolean consumedInline = (policy != BackpressurePolicy.DROP);
        do {
            pIdx = producerIndex.get();
            cIdx = consumerIndex.get();
            if (pIdx - cIdx >= capacity) {
                if (policy == BackpressurePolicy.DROP) {
                    // 丢弃最旧：消费者追赶一个槽位
                    if (consumerIndex.compareAndSet(cIdx, cIdx + 1)) {
                        droppedCount.incrementAndGet();
                    }
                    continue;
                } else {
                    // BLOCK：自旋等待
                    Thread.onSpinWait();
                    continue;
                }
            }
        } while (!producerIndex.compareAndSet(pIdx, pIdx + 1));

        buffer[(int) (pIdx & (capacity - 1))] = event;
        publishedCount.incrementAndGet();
        // 通知消费者
        for (Subscriber s : subscribers) {
            try {
                s.onEvent(event);
            } catch (Exception e) {
                log.warn("subscriber {} failed: {}", s.getClass().getSimpleName(), e.getMessage());
            }
        }
        // 仅 BLOCK 模式下立即推进 consumerIndex（fire-and-forget 语义）；
        // DROP 模式下保持 producer/consumer 解耦，消费者需要主动 advance。
        if (consumedInline) {
            consumerIndex.lazySet(pIdx + 1);
        }
    }

    /**
     * 读取最近 N 个事件（线程安全的快照）。
     */
    public List<BattleEvent> recent(int n) {
        long count = Math.min(n, publishedCount.get());
        List<BattleEvent> out = new java.util.ArrayList<>((int) count);
        long end = producerIndex.get();
        long start = Math.max(0, end - count);
        for (long i = start; i < end; i++) {
            BattleEvent e = buffer[(int) (i & (capacity - 1))];
            if (e != null) out.add(e);
        }
        return out;
    }

    public interface Subscriber {
        void onEvent(BattleEvent event);
    }

    /** 过滤特定阵营事件 */
    public static Subscriber forTeam(BattleEventBus bus, Team team, Subscriber delegate) {
        return event -> {
            Team[] ts = event.involvedTeams();
            if (ts.length == 0 || contains(ts, team)) {
                delegate.onEvent(event);
            }
        };
    }

    private static boolean contains(Team[] arr, Team t) {
        for (Team x : arr) if (x == t) return true;
        return false;
    }
}
