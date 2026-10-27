/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.MultiExecutionCallback;
import com.hazelcast.durableexecutor.DurableExecutorService;
import com.hazelcast.durableexecutor.DurableExecutorServiceFuture;
import com.hazelcast.map.IMap;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.IScheduledFuture;
import com.hazelcast.scheduledexecutor.TaskUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the distributed executor services: the executor, the durable executor, and the
 * scheduled executor.
 *
 * Two embedded members form a warehouse cluster that keeps stock levels in a partitioned map.
 * The warehouse ships serializable tasks to the members: a stock count fans out to every member
 * and sums the entries each one owns, a reservation runs on the member that owns the item's
 * partition, a restock is submitted to the durable executor and its result is retrieved later
 * by task id, and an end-of-day close runs as a named task on the scheduled executor. Every
 * task reaches the member's data through the instance Hazelcast injects into it.
 */
class WarehouseTaskExecutorTest {

    private static final String STOCK_MAP = "stock";

    private HazelcastInstance first;

    private HazelcastInstance second;

    @BeforeEach
    void startWarehouse() throws InterruptedException {
        String clusterName = "warehouse-" + UUID.randomUUID();
        first = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        second = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        assertThat(first.getCluster().getMembers()).hasSize(2);
        awaitClusterSafe();

        IMap<String, Integer> stock = first.getMap(STOCK_MAP);
        for (int item = 1; item <= 40; item++) {
            stock.put(sku(item), item);
        }
    }

    @AfterEach
    void stopWarehouse() {
        if (second != null) {
            second.shutdown();
        }
        if (first != null) {
            first.shutdown();
        }
    }

    @Test
    void stockCountFansOutToEveryMemberAndCoversEveryItem() throws Exception {
        IExecutorService executor = first.getExecutorService("inventory");

        Map<Member, Future<Long>> perMember = executor.submitToAllMembers(new CountLocalStock());

        assertThat(perMember.keySet()).containsExactlyInAnyOrderElementsOf(first.getCluster().getMembers());
        long units = 0;
        for (Future<Long> future : perMember.values()) {
            long local = future.get(30, TimeUnit.SECONDS);
            assertThat(local).isPositive();
            units += local;
        }
        assertThat(units).isEqualTo(40L * 41L / 2L);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Map<Member, Object>> results = new AtomicReference<>();
        executor.submitToAllMembers(new CountLocalStock(), new MultiExecutionCallback() {
            @Override
            public void onResponse(Member member, Object value) {
            }

            @Override
            public void onComplete(Map<Member, Object> values) {
                results.set(values);
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        long callbackUnits = results.get().values().stream().mapToLong(value -> (Long) value).sum();
        assertThat(callbackUnits).isEqualTo(units);
    }

    @Test
    void reservationRunsOnTheMemberThatOwnsTheItem() throws Exception {
        IExecutorService executor = second.getExecutorService("inventory");
        String item = sku(17);

        Reservation reservation = executor.submitToKeyOwner(new ReserveStock(item, 5), item)
                .get(30, TimeUnit.SECONDS);

        Member owner = first.getPartitionService().getPartition(item).getOwner();
        assertThat(reservation.memberUuid()).isEqualTo(owner.getUuid());
        assertThat(reservation.sku()).isEqualTo(item);
        assertThat(reservation.remaining()).isEqualTo(12);
        IMap<String, Integer> stock = first.getMap(STOCK_MAP);
        assertThat(stock.get(item)).isEqualTo(12);
    }

    @Test
    void durableRestockResultIsRetrievedByTaskId() throws Exception {
        DurableExecutorService durable = first.getDurableExecutorService("restock");
        String item = sku(3);

        DurableExecutorServiceFuture<Integer> submitted = durable.submitToKeyOwner(new Restock(item, 50), item);
        long taskId = submitted.getTaskId();
        assertThat(submitted.get(30, TimeUnit.SECONDS)).isEqualTo(53);

        Future<Integer> retrieved = second.getDurableExecutorService("restock").retrieveResult(taskId);
        assertThat(retrieved.get(30, TimeUnit.SECONDS)).isEqualTo(53);
        IMap<String, Integer> stock = second.getMap(STOCK_MAP);
        assertThat(stock.get(item)).isEqualTo(53);
    }

    @Test
    void namedEndOfDayCloseRunsOnTheScheduledExecutor() throws Exception {
        IScheduledExecutorService scheduler = first.getScheduledExecutorService("closing");

        IScheduledFuture<Long> close = scheduler.schedule(
                TaskUtils.named("end-of-day-close", new CloseDay()), 100, TimeUnit.MILLISECONDS);

        assertThat(close.getHandler().getTaskName()).isEqualTo("end-of-day-close");
        assertThat(close.get(30, TimeUnit.SECONDS)).isEqualTo(40L * 41L / 2L);
        assertThat(close.isDone()).isTrue();
        assertThat(close.getStats().getTotalRuns()).isEqualTo(1);
        IMap<String, Long> ledger = first.getMap("closing-ledger");
        assertThat(ledger.get("end-of-day")).isEqualTo(40L * 41L / 2L);
        close.dispose();
    }

    private static Config memberConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        return config;
    }

    private void awaitClusterSafe() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!first.getPartitionService().isClusterSafe()) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private static String sku(int item) {
        return String.format("SKU-%03d", item);
    }

    /** Sums the stock of the items whose partitions the executing member owns. */
    static final class CountLocalStock implements Callable<Long>, Serializable, HazelcastInstanceAware {

        private transient HazelcastInstance instance;

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.instance = hazelcastInstance;
        }

        @Override
        public Long call() {
            IMap<String, Integer> stock = instance.getMap(STOCK_MAP);
            long units = 0;
            for (String item : stock.localKeySet()) {
                units += stock.get(item);
            }
            return units;
        }
    }

    /** Takes units of one item out of stock on the member that owns it. */
    static final class ReserveStock implements Callable<Reservation>, Serializable, HazelcastInstanceAware {

        private final String sku;

        private final int quantity;

        private transient HazelcastInstance instance;

        ReserveStock(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.instance = hazelcastInstance;
        }

        @Override
        public Reservation call() {
            IMap<String, Integer> stock = instance.getMap(STOCK_MAP);
            int remaining = stock.get(sku) - quantity;
            stock.put(sku, remaining);
            return new Reservation(instance.getCluster().getLocalMember().getUuid(), sku, remaining);
        }
    }

    /** Adds units of one item to stock and reports the new level. */
    static final class Restock implements Callable<Integer>, Serializable, HazelcastInstanceAware {

        private final String sku;

        private final int quantity;

        private transient HazelcastInstance instance;

        Restock(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.instance = hazelcastInstance;
        }

        @Override
        public Integer call() {
            IMap<String, Integer> stock = instance.getMap(STOCK_MAP);
            return stock.merge(sku, quantity, Integer::sum);
        }
    }

    /** Totals the whole stock and records it in the closing ledger. */
    static final class CloseDay implements Callable<Long>, Serializable, HazelcastInstanceAware {

        private transient HazelcastInstance instance;

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.instance = hazelcastInstance;
        }

        @Override
        public Long call() {
            IMap<String, Integer> stock = instance.getMap(STOCK_MAP);
            long units = stock.values().stream().mapToLong(Integer::longValue).sum();
            IMap<String, Long> ledger = instance.getMap("closing-ledger");
            ledger.put("end-of-day", units);
            return units;
        }
    }

    /** The outcome of a reservation and the member that performed it. */
    record Reservation(UUID memberUuid, String sku, int remaining) implements Serializable {
    }
}
