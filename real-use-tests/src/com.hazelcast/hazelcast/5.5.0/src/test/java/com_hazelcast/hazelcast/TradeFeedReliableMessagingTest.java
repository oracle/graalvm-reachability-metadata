/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.ReliableTopicConfig;
import com.hazelcast.config.RingbufferConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IFunction;
import com.hazelcast.ringbuffer.OverflowPolicy;
import com.hazelcast.ringbuffer.ReadResultSet;
import com.hazelcast.ringbuffer.Ringbuffer;
import com.hazelcast.ringbuffer.StaleSequenceException;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.ReliableMessageListener;
import com.hazelcast.topic.TopicOverloadException;
import com.hazelcast.topic.TopicOverloadPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises reliable messaging: ringbuffers and the reliable topics built on them.
 *
 * Two embedded members form a trading desk cluster. The desk keeps a bounded audit trail in a
 * ringbuffer whose oldest entries are overwritten, reads it back by sequence and in filtered
 * batches, and refuses new orders on a time-to-live window once it is full. Trades are
 * published on a reliable topic: listeners on both members receive them in publication order,
 * a late listener replays the history from a stored sequence, and an alert topic whose
 * ringbuffer is full rejects publication under the error overload policy.
 */
class TradeFeedReliableMessagingTest {

    private static final String AUDIT = "audit";

    private static final String ORDER_WINDOW = "order-window";

    private static final String TRADES = "trades";

    private static final String ALERTS = "alerts";

    private HazelcastInstance first;

    private HazelcastInstance second;

    @BeforeEach
    void openDesk() {
        String clusterName = "trading-desk-" + UUID.randomUUID();
        first = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        second = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        assertThat(first.getCluster().getMembers()).hasSize(2);
    }

    @AfterEach
    void closeDesk() {
        if (second != null) {
            second.shutdown();
        }
        if (first != null) {
            first.shutdown();
        }
    }

    @Test
    void auditTrailKeepsTheNewestEntriesInSequenceOrder() throws Exception {
        Ringbuffer<String> audit = first.getRingbuffer(AUDIT);
        assertThat(audit.capacity()).isEqualTo(5);
        assertThat(audit.tailSequence()).isEqualTo(-1);

        String[] entries = {
            "LOGIN alice", "ORDER alice ACME 10", "ORDER alice INIT 5", "LOGIN bob",
            "ORDER bob ACME 3", "LOGOUT alice", "ORDER bob ZETA 7", "LOGOUT bob"
        };
        for (int index = 0; index < 5; index++) {
            assertThat(audit.add(entries[index])).isEqualTo(index);
        }
        List<String> rest = List.of(entries[5], entries[6], entries[7]);
        assertThat(audit.addAllAsync(rest, OverflowPolicy.OVERWRITE).toCompletableFuture().get(30, TimeUnit.SECONDS))
                .isEqualTo(7L);

        Ringbuffer<String> replica = second.getRingbuffer(AUDIT);
        assertThat(replica.size()).isEqualTo(5);
        assertThat(replica.headSequence()).isEqualTo(3);
        assertThat(replica.tailSequence()).isEqualTo(7);
        assertThat(replica.readOne(3)).isEqualTo("LOGIN bob");
        assertThat(replica.readOne(7)).isEqualTo("LOGOUT bob");
        assertThatThrownBy(() -> replica.readOne(1)).isInstanceOf(StaleSequenceException.class);

        ReadResultSet<String> orders = replica.readManyAsync(replica.headSequence(), 1, 5, new OrdersOnly())
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertThat(orders.readCount()).isEqualTo(5);
        assertThat(orders.size()).isEqualTo(2);
        assertThat(orders.get(0)).isEqualTo("ORDER bob ACME 3");
        assertThat(orders.getSequence(0)).isEqualTo(4);
        assertThat(orders.get(1)).isEqualTo("ORDER bob ZETA 7");
        assertThat(orders.getSequence(1)).isEqualTo(6);
        assertThat(orders.getNextSequenceToReadFrom()).isEqualTo(8);
    }

    @Test
    void fullOrderWindowRejectsNewOrdersUntilTheyExpire() throws Exception {
        Ringbuffer<String> window = first.getRingbuffer(ORDER_WINDOW);
        assertThat(window.addAsync("ACME 10", OverflowPolicy.FAIL).toCompletableFuture().get(30, TimeUnit.SECONDS))
                .isZero();
        assertThat(window.addAsync("ZETA 7", OverflowPolicy.FAIL).toCompletableFuture().get(30, TimeUnit.SECONDS))
                .isEqualTo(1L);
        assertThat(window.remainingCapacity()).isZero();

        Ringbuffer<String> otherDesk = second.getRingbuffer(ORDER_WINDOW);
        assertThat(otherDesk.addAsync("INIT 5", OverflowPolicy.FAIL).toCompletableFuture().get(30, TimeUnit.SECONDS))
                .isEqualTo(-1L);
        assertThat(otherDesk.tailSequence()).isEqualTo(1);
        assertThat(otherDesk.readOne(0)).isEqualTo("ACME 10");
    }

    @Test
    void tradesReachListenersOnEveryMemberInPublicationOrder() throws Exception {
        List<Trade> trades = List.of(
                new Trade("ACME", 10, 12_550), new Trade("INIT", 5, 3_020),
                new Trade("ACME", -4, 12_600), new Trade("ZETA", 7, 980));
        TradeBook firstBook = new TradeBook(-1, trades.size());
        TradeBook secondBook = new TradeBook(-1, trades.size());

        ITopic<Trade> published = first.getReliableTopic(TRADES);
        UUID firstRegistration = published.addMessageListener(firstBook);
        UUID secondRegistration = second.<Trade>getReliableTopic(TRADES).addMessageListener(secondBook);
        for (Trade trade : trades.subList(0, 2)) {
            published.publish(trade);
        }
        published.publishAll(trades.subList(2, 4));

        assertThat(firstBook.received.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(secondBook.received.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(firstBook.trades).containsExactlyElementsOf(trades);
        assertThat(secondBook.trades).containsExactlyElementsOf(trades);
        assertThat(secondBook.publishers).containsOnly(first.getCluster().getLocalMember().getUuid());
        assertThat(secondBook.lastSequence).isEqualTo(3);

        assertThat(published.removeMessageListener(firstRegistration)).isTrue();
        assertThat(second.getReliableTopic(TRADES).removeMessageListener(secondRegistration)).isTrue();
    }

    @Test
    void lateListenerReplaysTradesFromItsStoredSequence() throws Exception {
        ITopic<Trade> published = first.getReliableTopic(TRADES);
        List<Trade> trades = new ArrayList<>();
        for (int quantity = 1; quantity <= 6; quantity++) {
            Trade trade = new Trade("ACME", quantity, 12_500 + quantity);
            trades.add(trade);
            published.publish(trade);
        }

        TradeBook resumed = new TradeBook(2, 4);
        UUID registration = second.<Trade>getReliableTopic(TRADES).addMessageListener(resumed);
        assertThat(resumed.received.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(resumed.trades).containsExactlyElementsOf(trades.subList(2, 6));
        assertThat(resumed.lastSequence).isEqualTo(5);
        assertThat(second.getReliableTopic(TRADES).removeMessageListener(registration)).isTrue();
    }

    @Test
    void alertTopicRejectsPublicationWhenItsRingbufferIsFull() {
        ITopic<String> alerts = first.getReliableTopic(ALERTS);
        alerts.publish("ACME halted");
        alerts.publish("INIT halted");
        alerts.publish("ZETA halted");
        Ringbuffer<String> backing = first.getRingbuffer("_hz_rb_" + ALERTS);
        assertThat(backing.capacity()).isEqualTo(3);

        assertThatThrownBy(() -> second.<String>getReliableTopic(ALERTS).publish("market closed"))
                .isInstanceOf(TopicOverloadException.class);
        assertThat(backing.size()).isEqualTo(3);
        assertThat(backing.remainingCapacity()).isZero();
    }

    private static Config memberConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.addRingBufferConfig(new RingbufferConfig(AUDIT).setCapacity(5).setBackupCount(1));
        config.addRingBufferConfig(new RingbufferConfig(ORDER_WINDOW).setCapacity(2).setTimeToLiveSeconds(3600));
        config.addRingBufferConfig(new RingbufferConfig(ALERTS).setCapacity(3).setTimeToLiveSeconds(3600));
        config.addReliableTopicConfig(new ReliableTopicConfig(TRADES).setReadBatchSize(2));
        config.addReliableTopicConfig(new ReliableTopicConfig(ALERTS).setTopicOverloadPolicy(TopicOverloadPolicy.ERROR));
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        return config;
    }

    /** A trade the desk executed; a negative quantity is a sale. */
    record Trade(String symbol, int quantity, long priceCents) implements Serializable {
    }

    /** Keeps only order entries of the audit trail. */
    static final class OrdersOnly implements IFunction<String, Boolean> {

        @Override
        public Boolean apply(String entry) {
            return entry.startsWith("ORDER ");
        }
    }

    /** Records trades in arrival order and remembers the last sequence it has processed. */
    static final class TradeBook implements ReliableMessageListener<Trade> {

        private final long initialSequence;

        private final CountDownLatch received;

        private final List<Trade> trades = new CopyOnWriteArrayList<>();

        private final List<UUID> publishers = new CopyOnWriteArrayList<>();

        private volatile long lastSequence = -1;

        TradeBook(long initialSequence, int expected) {
            this.initialSequence = initialSequence;
            this.received = new CountDownLatch(expected);
        }

        @Override
        public void onMessage(Message<Trade> message) {
            trades.add(message.getMessageObject());
            publishers.add(message.getPublishingMember().getUuid());
            received.countDown();
        }

        @Override
        public long retrieveInitialSequence() {
            return initialSequence;
        }

        @Override
        public void storeSequence(long sequence) {
            lastSequence = sequence;
        }

        @Override
        public boolean isLossTolerant() {
            return false;
        }

        @Override
        public boolean isTerminal(Throwable failure) {
            return true;
        }
    }
}
