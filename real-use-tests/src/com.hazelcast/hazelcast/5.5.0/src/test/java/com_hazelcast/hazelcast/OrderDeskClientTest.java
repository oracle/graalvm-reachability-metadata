/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Address;
import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.nearcache.NearCacheStats;
import com.hazelcast.topic.ITopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Hazelcast Java client talking to a cluster member over the client protocol.
 *
 * An embedded member hosts an order desk's data, and the desk application connects to it with
 * `HazelcastClient` instead of joining the cluster. The client stores orders that the member
 * reads back, receives entry events for orders the member adds and updates, serves repeated
 * status lookups from its Near Cache until the member changes a status and invalidates it, and
 * broadcasts dispatch notices on a topic. Shutting the client down is reported to its
 * lifecycle listener while the member keeps every order.
 */
class OrderDeskClientTest {

    private static final String ORDERS_MAP = "orders";

    private static final String STATUS_MAP = "order-status";

    private static final String DISPATCH_TOPIC = "dispatch";

    private HazelcastInstance member;

    private HazelcastInstance client;

    @BeforeEach
    void startDesk() {
        String clusterName = "order-desk-" + UUID.randomUUID();
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);

        Address memberAddress = member.getCluster().getLocalMember().getAddress();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(clusterName);
        clientConfig.setInstanceName("order-desk-client");
        clientConfig.setProperty("hazelcast.phone.home.enabled", "false");
        clientConfig.getNetworkConfig().getAutoDetectionConfig().setEnabled(false);
        clientConfig.getNetworkConfig().addAddress(memberAddress.getHost() + ":" + memberAddress.getPort());
        clientConfig.addNearCacheConfig(new NearCacheConfig(STATUS_MAP)
                .setInMemoryFormat(InMemoryFormat.OBJECT)
                .setInvalidateOnChange(true));
        client = HazelcastClient.newHazelcastClient(clientConfig);
    }

    @AfterEach
    void stopDesk() {
        if (client != null) {
            client.shutdown();
        }
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void deskClientSharesOrdersWithTheCluster() throws InterruptedException {
        assertThat(client.getName()).isEqualTo("order-desk-client");
        assertThat(client.getCluster().getMembers())
                .containsExactly(member.getCluster().getLocalMember());

        IMap<String, Order> clientOrders = client.getMap(ORDERS_MAP);
        IMap<String, Order> memberOrders = member.getMap(ORDERS_MAP);

        clientOrders.put("A-100", new Order("A-100", "Ada", 3, 4200));
        clientOrders.put("A-101", new Order("A-101", "Grace", 1, 990));
        assertThat(memberOrders.get("A-100")).isEqualTo(new Order("A-100", "Ada", 3, 4200));
        assertThat(memberOrders.size()).isEqualTo(2);

        OrderEvents events = new OrderEvents(2);
        UUID registration = clientOrders.addEntryListener(events, true);
        memberOrders.put("A-102", new Order("A-102", "Linus", 2, 1500));
        memberOrders.put("A-101", new Order("A-101", "Grace", 4, 3960));
        assertThat(events.received.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(events.log).containsExactlyInAnyOrder("added A-102 x2", "updated A-101 x1->x4");
        assertThat(clientOrders.removeEntryListener(registration)).isTrue();

        assertThat(clientOrders.executeOnKey("A-100", new AddItem(1400))).isEqualTo(4);
        assertThat(memberOrders.get("A-100").totalCents()).isEqualTo(5600);

        IMap<String, String> memberStatuses = member.getMap(STATUS_MAP);
        memberStatuses.put("A-100", "PACKING");
        IMap<String, String> clientStatuses = client.getMap(STATUS_MAP);
        assertThat(clientStatuses.get("A-100")).isEqualTo("PACKING");
        assertThat(clientStatuses.get("A-100")).isEqualTo("PACKING");
        assertThat(clientStatuses.get("A-100")).isEqualTo("PACKING");
        NearCacheStats nearCache = clientStatuses.getLocalMapStats().getNearCacheStats();
        assertThat(nearCache.getMisses()).isEqualTo(1L);
        assertThat(nearCache.getHits()).isEqualTo(2L);
        assertThat(nearCache.getOwnedEntryCount()).isEqualTo(1L);

        memberStatuses.put("A-100", "SHIPPED");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!"SHIPPED".equals(clientStatuses.get("A-100")) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(clientStatuses.get("A-100")).isEqualTo("SHIPPED");

        CountDownLatch noticesSeen = new CountDownLatch(2);
        List<String> notices = new CopyOnWriteArrayList<>();
        ITopic<String> memberDispatch = member.getTopic(DISPATCH_TOPIC);
        memberDispatch.addMessageListener(message -> {
            notices.add(message.getMessageObject());
            noticesSeen.countDown();
        });
        ITopic<String> clientDispatch = client.getTopic(DISPATCH_TOPIC);
        clientDispatch.publish("A-100 left dock 3");
        clientDispatch.publish("A-102 left dock 1");
        assertThat(noticesSeen.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(notices).containsExactly("A-100 left dock 3", "A-102 left dock 1");

        CountDownLatch clientStopped = new CountDownLatch(1);
        client.getLifecycleService().addLifecycleListener(event -> {
            if (event.getState() == LifecycleEvent.LifecycleState.SHUTDOWN) {
                clientStopped.countDown();
            }
        });
        client.shutdown();
        assertThat(clientStopped.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getLifecycleService().isRunning()).isFalse();
        assertThat(memberOrders.keySet()).containsExactlyInAnyOrder("A-100", "A-101", "A-102");
    }

    /**
     * An order as the desk records it, with its total in cents.
     */
    public record Order(String id, String customer, int quantity, long totalCents) implements Serializable {
    }

    /**
     * Adds one item at the given price to an order on the member that owns it.
     */
    static final class AddItem implements EntryProcessor<String, Order, Integer> {

        private final long priceCents;

        AddItem(long priceCents) {
            this.priceCents = priceCents;
        }

        @Override
        public Integer process(Map.Entry<String, Order> entry) {
            Order order = entry.getValue();
            entry.setValue(new Order(order.id(), order.customer(), order.quantity() + 1,
                    order.totalCents() + priceCents));
            return order.quantity() + 1;
        }
    }

    /**
     * Records the order events the client receives, in arrival order.
     */
    static final class OrderEvents implements EntryAddedListener<String, Order>, EntryUpdatedListener<String, Order> {

        private final List<String> log = new CopyOnWriteArrayList<>();

        private final CountDownLatch received;

        OrderEvents(int expected) {
            received = new CountDownLatch(expected);
        }

        @Override
        public void entryAdded(EntryEvent<String, Order> event) {
            log.add("added " + event.getKey() + " x" + event.getValue().quantity());
            received.countDown();
        }

        @Override
        public void entryUpdated(EntryEvent<String, Order> event) {
            log.add("updated " + event.getKey() + " x" + event.getOldValue().quantity()
                    + "->x" + event.getValue().quantity());
            received.countDown();
        }
    }
}
