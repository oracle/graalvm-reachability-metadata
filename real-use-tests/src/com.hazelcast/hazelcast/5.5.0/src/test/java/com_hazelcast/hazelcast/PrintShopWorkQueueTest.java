/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cluster.Member;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.collection.QueueStore;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.config.QueueStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ItemEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises distributed work queues: IQueue with a QueueStore, bounded capacity, a priority
 * comparator, item listeners and backups.
 *
 * A print shop hands work to its presses through cluster queues. Print jobs are written through
 * to a job store, so a shop that restarts resumes the jobs nobody took. The intake queue is
 * bounded: a full queue refuses a quick offer and admits a waiting one as soon as a press takes
 * a job, while a listener on another member sees every job arrive and leave. Support tickets
 * wait in a priority queue whose polls serve the most severe ticket first, and that order
 * survives the member holding the queue being killed.
 */
class PrintShopWorkQueueTest {

    private static final String PRINT_JOBS = "print-jobs";

    private static final String INTAKE = "intake";

    private static final String TICKETS = "tickets";

    private final List<HazelcastInstance> members = new ArrayList<>();

    @AfterEach
    void closeShop() {
        for (HazelcastInstance member : members) {
            if (member.getLifecycleService().isRunning()) {
                member.shutdown();
            }
        }
    }

    @Test
    void restartedShopResumesPendingJobsFromTheJobStore() {
        JobStore store = new JobStore();
        String clusterName = "print-shop-" + UUID.randomUUID();

        HazelcastInstance shop = start(clusterName, store);
        IQueue<PrintJob> jobs = shop.getQueue(PRINT_JOBS);
        assertThat(jobs.offer(new PrintJob("flyers", 500))).isTrue();
        assertThat(jobs.offer(new PrintJob("posters", 40))).isTrue();
        assertThat(jobs.offer(new PrintJob("menus", 120))).isTrue();
        assertThat(jobs.offer(new PrintJob("cards", 1_000))).isTrue();
        assertThat(store.jobs()).extracting(PrintJob::title)
                .containsExactly("flyers", "posters", "menus", "cards");

        assertThat(jobs.poll()).isEqualTo(new PrintJob("flyers", 500));
        assertThat(store.jobs()).extracting(PrintJob::title).containsExactly("posters", "menus", "cards");
        shop.shutdown();

        HazelcastInstance reopened = start(clusterName, store);
        IQueue<PrintJob> resumed = reopened.getQueue(PRINT_JOBS);
        assertThat(resumed.size()).isEqualTo(3);
        assertThat(resumed.poll()).isEqualTo(new PrintJob("posters", 40));
        assertThat(resumed.poll()).isEqualTo(new PrintJob("menus", 120));
        assertThat(resumed.offer(new PrintJob("banners", 8))).isTrue();

        List<PrintJob> remaining = new ArrayList<>();
        assertThat(resumed.drainTo(remaining)).isEqualTo(2);
        assertThat(remaining).extracting(PrintJob::title).containsExactly("cards", "banners");
        assertThat(store.jobs()).isEmpty();
    }

    @Test
    void fullIntakeAdmitsAWaitingJobOnceAPressTakesOne() throws Exception {
        String clusterName = "print-shop-" + UUID.randomUUID();
        HazelcastInstance counter = start(clusterName, new JobStore());
        HazelcastInstance press = start(clusterName, new JobStore());
        assertThat(press.getCluster().getMembers()).hasSize(2);

        IntakeLog log = new IntakeLog(3, 2);
        IQueue<String> watched = press.getQueue(INTAKE);
        watched.addItemListener(log, true);

        IQueue<String> intake = counter.getQueue(INTAKE);
        assertThat(intake.offer("order-1")).isTrue();
        assertThat(intake.offer("order-2")).isTrue();
        assertThat(intake.remainingCapacity()).isZero();
        assertThat(intake.offer("order-3", 50, TimeUnit.MILLISECONDS)).isFalse();

        ExecutorService clerk = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> waiting = clerk.submit(() -> intake.offer("order-4", 30, TimeUnit.SECONDS));
            assertThat(watched.take()).isEqualTo("order-1");
            assertThat(waiting.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            clerk.shutdownNow();
        }
        assertThat(watched.poll(30, TimeUnit.SECONDS)).isEqualTo("order-2");
        assertThat(intake).containsExactly("order-4");

        assertThat(log.added.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(log.removed.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(log.arrivals).containsExactly("order-1", "order-2", "order-4");
        assertThat(log.departures).containsExactly("order-1", "order-2");
    }

    @Test
    void mostSevereTicketIsServedFirstEvenAfterTheOwningMemberDies() {
        String clusterName = "print-shop-" + UUID.randomUUID();
        HazelcastInstance first = start(clusterName, new JobStore());
        HazelcastInstance second = start(clusterName, new JobStore());
        assertThat(second.getCluster().getMembers()).hasSize(2);

        IQueue<Ticket> tickets = first.getQueue(TICKETS);
        tickets.add(new Ticket(1, 2, "toner low"));
        tickets.add(new Ticket(2, 5, "press jammed"));
        tickets.add(new Ticket(3, 1, "paper order"));
        tickets.add(new Ticket(4, 5, "fuser overheating"));
        tickets.add(new Ticket(5, 3, "color drift"));
        assertThat(second.<Ticket>getQueue(TICKETS).peek()).isEqualTo(new Ticket(2, 5, "press jammed"));

        Member owner = first.getPartitionService().getPartition(TICKETS).getOwner();
        HazelcastInstance doomed = owner.equals(first.getCluster().getLocalMember()) ? first : second;
        HazelcastInstance survivor = doomed == first ? second : first;
        doomed.getLifecycleService().terminate();

        IQueue<Ticket> remaining = survivor.getQueue(TICKETS);
        assertThat(remaining.poll()).isEqualTo(new Ticket(2, 5, "press jammed"));
        List<Long> served = new ArrayList<>();
        for (Ticket next = remaining.poll(); next != null; next = remaining.poll()) {
            served.add(next.id());
        }
        assertThat(served).containsExactly(4L, 5L, 1L, 3L);
        assertThat(remaining.isEmpty()).isTrue();
    }

    private HazelcastInstance start(String clusterName, JobStore store) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        QueueStoreConfig storeConfig = new QueueStoreConfig()
                .setEnabled(true)
                .setStoreImplementation(store)
                .setProperty("binary", "false");
        config.addQueueConfig(new QueueConfig(PRINT_JOBS).setQueueStoreConfig(storeConfig));
        config.addQueueConfig(new QueueConfig(INTAKE).setMaxSize(2).setBackupCount(1));
        config.addQueueConfig(new QueueConfig(TICKETS)
                .setBackupCount(1)
                .setPriorityComparatorClassName(TicketPriority.class.getName()));
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        HazelcastInstance member = Hazelcast.newHazelcastInstance(config);
        members.add(member);
        return member;
    }

    /// A job sent to the presses.
    record PrintJob(String title, int copies) implements Serializable {
    }

    /// A support ticket; a higher severity is more urgent.
    record Ticket(long id, int severity, String summary) implements Serializable {
    }

    /// Serves the most severe ticket first and, among equals, the oldest.
    public static class TicketPriority implements Comparator<Ticket> {

        @Override
        public int compare(Ticket left, Ticket right) {
            int bySeverity = Integer.compare(right.severity(), left.severity());
            return bySeverity != 0 ? bySeverity : Long.compare(left.id(), right.id());
        }
    }

    /// The job store the print-jobs queue writes through to, keyed by queue item id.
    static class JobStore implements QueueStore<PrintJob> {

        private final NavigableMap<Long, PrintJob> rows = new ConcurrentSkipListMap<>();

        List<PrintJob> jobs() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void store(Long key, PrintJob value) {
            rows.put(key, value);
        }

        @Override
        public void storeAll(Map<Long, PrintJob> map) {
            rows.putAll(map);
        }

        @Override
        public void delete(Long key) {
            rows.remove(key);
        }

        @Override
        public void deleteAll(Collection<Long> keys) {
            keys.forEach(rows::remove);
        }

        @Override
        public PrintJob load(Long key) {
            return rows.get(key);
        }

        @Override
        public Map<Long, PrintJob> loadAll(Collection<Long> keys) {
            Map<Long, PrintJob> loaded = new HashMap<>();
            for (Long key : keys) {
                PrintJob job = rows.get(key);
                if (job != null) {
                    loaded.put(key, job);
                }
            }
            return loaded;
        }

        @Override
        public Set<Long> loadAllKeys() {
            return new TreeSet<>(rows.keySet());
        }
    }

    /// Records the intake traffic one member observes.
    static class IntakeLog implements ItemListener<String> {

        final List<String> arrivals = new CopyOnWriteArrayList<>();

        final List<String> departures = new CopyOnWriteArrayList<>();

        final CountDownLatch added;

        final CountDownLatch removed;

        IntakeLog(int expectedArrivals, int expectedDepartures) {
            added = new CountDownLatch(expectedArrivals);
            removed = new CountDownLatch(expectedDepartures);
        }

        @Override
        public void itemAdded(ItemEvent<String> event) {
            assertThat(event.getEventType()).isEqualTo(ItemEventType.ADDED);
            arrivals.add(event.getItem());
            added.countDown();
        }

        @Override
        public void itemRemoved(ItemEvent<String> event) {
            assertThat(event.getEventType()).isEqualTo(ItemEventType.REMOVED);
            departures.add(event.getItem());
            removed.countDown();
        }
    }
}
