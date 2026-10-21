/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapInterceptor;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises IMap entry lifecycle and concurrency control: pessimistic key locks, optimistic
 * versioned updates, time-to-live and max-idle expiry, size-bounded eviction, and map interceptors.
 *
 * A help desk keeps its working state on one embedded member. Agents claim tickets with key locks
 * (one with a lease that the member releases on its own) and update them with compare-and-replace
 * guarded by the entry version. One-time login codes expire after a map-wide time to live while
 * pinned and idle-bounded entries override it, a small per-partition cache of customer profiles
 * evicts its least frequently read entry, and an interceptor normalises and protects agent emails.
 */
class HelpdeskSessionLifecycleTest {

    private static final String TICKETS = "tickets";

    private static final String LOGIN_CODES = "login-codes";

    private static final String PROFILE_CACHE = "profile-cache";

    private static final String AGENT_EMAILS = "agent-emails";

    private static final long EVENT_TIMEOUT_SECONDS = 60;

    private HazelcastInstance member;

    private ExecutorService otherAgent;

    @BeforeEach
    void startHelpdesk() {
        Config config = new Config();
        config.setClusterName("helpdesk-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.partition.count", "1");
        config.setProperty("hazelcast.internal.map.expiration.task.period.seconds", "1");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);

        config.addMapConfig(new MapConfig(LOGIN_CODES).setTimeToLiveSeconds(1));
        config.addMapConfig(new MapConfig(PROFILE_CACHE).setEvictionConfig(new EvictionConfig()
                .setEvictionPolicy(EvictionPolicy.LFU)
                .setMaxSizePolicy(MaxSizePolicy.PER_PARTITION)
                .setSize(3)));

        member = Hazelcast.newHazelcastInstance(config);
        otherAgent = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopHelpdesk() throws InterruptedException {
        otherAgent.shutdownNow();
        assertThat(otherAgent.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void agentsCoordinateTicketUpdatesWithLocksAndVersions() throws Exception {
        IMap<String, String> tickets = member.getMap(TICKETS);
        tickets.put("T-100", "open");
        tickets.put("T-200", "open");

        tickets.lock("T-100");
        assertThat(tickets.isLocked("T-100")).isTrue();
        assertThat(otherAgent.submit(() -> tickets.tryLock("T-100")).get()).isFalse();
        assertThat(otherAgent.submit(() -> tickets.tryPut("T-100", "stolen", 50, TimeUnit.MILLISECONDS)).get())
                .isFalse();

        tickets.put("T-100", "in-progress");
        tickets.unlock("T-100");
        assertThat(tickets.isLocked("T-100")).isFalse();
        assertThat(otherAgent.submit(() -> {
            boolean claimed = tickets.tryLock("T-100");
            tickets.put("T-100", "escalated");
            tickets.unlock("T-100");
            return claimed;
        }).get()).isTrue();
        assertThat(tickets.get("T-100")).isEqualTo("escalated");

        tickets.lock("T-200", 500, TimeUnit.MILLISECONDS);
        assertThat(otherAgent.submit(() -> {
            boolean claimed = tickets.tryLock("T-200", 30, TimeUnit.SECONDS);
            tickets.unlock("T-200");
            return claimed;
        }).get()).isTrue();
        assertThatThrownBy(() -> tickets.unlock("T-200")).isInstanceOf(IllegalMonitorStateException.class);

        EntryView<String, String> seen = tickets.getEntryView("T-200");
        assertThat(seen.getValue()).isEqualTo("open");
        tickets.put("T-200", "waiting-on-customer");
        EntryView<String, String> current = tickets.getEntryView("T-200");
        assertThat(current.getVersion()).isGreaterThan(seen.getVersion());
        assertThat(tickets.replace("T-200", seen.getValue(), "closed")).isFalse();
        assertThat(tickets.replace("T-200", current.getValue(), "closed")).isTrue();
        assertThat(tickets.get("T-200")).isEqualTo("closed");
    }

    @Test
    void loginCodesExpireWhileOverriddenEntriesFollowTheirOwnLifetime() throws InterruptedException {
        IMap<String, String> codes = member.getMap(LOGIN_CODES);
        Map<String, String> expired = new ConcurrentHashMap<>();
        CountDownLatch bothExpired = new CountDownLatch(2);
        codes.addEntryListener((EntryExpiredListener<String, String>) (EntryEvent<String, String> event) -> {
            expired.put(event.getKey(), event.getOldValue());
            bothExpired.countDown();
        }, true);

        codes.put("alice", "481516");
        codes.put("bob", "234200", 0, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
        codes.put("kiosk", "000000", 0, TimeUnit.SECONDS);

        assertThat(codes.getEntryView("alice").getTtl()).isEqualTo(TimeUnit.SECONDS.toMillis(1));
        assertThat(codes.getEntryView("kiosk").getTtl()).isEqualTo(Long.MAX_VALUE);

        assertThat(bothExpired.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(expired).containsOnly(Map.entry("alice", "481516"), Map.entry("bob", "234200"));
        assertThat(codes.get("alice")).isNull();
        assertThat(codes.get("bob")).isNull();
        assertThat(codes.get("kiosk")).isEqualTo("000000");
        assertThat(codes.size()).isEqualTo(1);
    }

    @Test
    void profileCacheEvictsTheLeastFrequentlyReadCustomer() throws InterruptedException {
        IMap<String, String> profiles = member.getMap(PROFILE_CACHE);
        Map<String, String> evicted = new ConcurrentHashMap<>();
        CountDownLatch oneEvicted = new CountDownLatch(1);
        profiles.addEntryListener((EntryEvictedListener<String, String>) (EntryEvent<String, String> event) -> {
            evicted.put(event.getKey(), event.getOldValue());
            oneEvicted.countDown();
        }, true);

        profiles.put("C-1", "Ada Lovelace");
        profiles.put("C-2", "Grace Hopper");
        profiles.put("C-3", "Edsger Dijkstra");
        for (int read = 0; read < 3; read++) {
            assertThat(profiles.get("C-1")).isEqualTo("Ada Lovelace");
            assertThat(profiles.get("C-3")).isEqualTo("Edsger Dijkstra");
        }
        assertThat(profiles.size()).isEqualTo(3);

        profiles.put("C-4", "Barbara Liskov");

        assertThat(oneEvicted.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(evicted).containsOnly(Map.entry("C-2", "Grace Hopper"));
        assertThat(profiles.size()).isEqualTo(3);
        assertThat(profiles.keySet()).containsExactlyInAnyOrder("C-1", "C-3", "C-4");
        assertThat(profiles.getLocalMapStats().getHits()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void interceptorNormalisesAndProtectsAgentEmails() {
        IMap<String, String> emails = member.getMap(AGENT_EMAILS);
        String registration = emails.addInterceptor(new EmailPolicy());

        emails.put("agent-7", "  Dana.Smith@Helpdesk.EXAMPLE ");
        emails.put("supervisor", "Lee.Park@helpdesk.example");

        assertThat(emails.get("agent-7")).isEqualTo("dana.smith@helpdesk.example");
        assertThat(emails.get("supervisor")).isEqualTo("lee.park@helpdesk.example");
        assertThatThrownBy(() -> emails.remove("supervisor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("supervisor");
        assertThat(emails.remove("agent-7")).isEqualTo("dana.smith@helpdesk.example");
        assertThat(emails.containsKey("supervisor")).isTrue();

        assertThat(emails.removeInterceptor(registration)).isTrue();
        emails.put("agent-8", "Kim@Helpdesk.example");
        assertThat(emails.get("agent-8")).isEqualTo("Kim@Helpdesk.example");
        assertThat(emails.remove("supervisor")).isEqualTo("lee.park@helpdesk.example");
    }

    /**
     * Stores agent emails trimmed and lower-cased, and refuses to remove a supervisor's address.
     */
    static final class EmailPolicy implements MapInterceptor {

        private static final long serialVersionUID = 1L;

        @Override
        public Object interceptGet(Object value) {
            return null;
        }

        @Override
        public void afterGet(Object value) {
        }

        @Override
        public Object interceptPut(Object oldValue, Object newValue) {
            return ((String) newValue).trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public void afterPut(Object value) {
        }

        @Override
        public Object interceptRemove(Object removedValue) {
            if (((String) removedValue).startsWith("lee.park@")) {
                throw new IllegalStateException("The supervisor address cannot be removed");
            }
            return null;
        }

        @Override
        public void afterRemove(Object oldValue) {
        }
    }
}
