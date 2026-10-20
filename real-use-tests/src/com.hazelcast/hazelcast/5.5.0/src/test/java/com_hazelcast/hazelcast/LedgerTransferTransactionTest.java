/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.collection.IQueue;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionException;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionOptions.TransactionType;
import com.hazelcast.transaction.TransactionalMap;
import com.hazelcast.transaction.TransactionalMultiMap;
import com.hazelcast.transaction.TransactionalQueue;
import com.hazelcast.transaction.TransactionalSet;
import com.hazelcast.transaction.TransactionalTaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the transaction subsystem: transaction contexts and transactional tasks over
 * transactional maps, queues, multimaps, and sets, with two-phase and one-phase commit.
 *
 * Two embedded members form a ledger cluster. A transfer debits one account, credits another,
 * records the movement in a per-account history, and queues a ledger event for an auditor, all
 * inside one transaction. Members outside the transaction keep reading committed state until
 * commit, a transfer the application rejects is rolled back without leaving any trace, a
 * pessimistic read locks the account for other callers, and a transactional task commits or
 * rolls back on its own depending on whether it throws.
 */
class LedgerTransferTransactionTest {

    private static final String ACCOUNTS = "accounts";

    private static final String LEDGER_EVENTS = "ledger-events";

    private static final String HISTORY = "account-history";

    private static final String AUDITED = "audited-transfers";

    private HazelcastInstance first;

    private HazelcastInstance second;

    @BeforeEach
    void startLedger() throws InterruptedException {
        String clusterName = "ledger-" + UUID.randomUUID();
        first = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        second = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        assertThat(first.getCluster().getMembers()).hasSize(2);
        awaitClusterSafe();

        IMap<String, Long> accounts = first.getMap(ACCOUNTS);
        accounts.put("alice", 500L);
        accounts.put("bob", 200L);
        accounts.put("carol", 50L);
    }

    @AfterEach
    void stopLedger() {
        if (second != null) {
            second.shutdown();
        }
        if (first != null) {
            first.shutdown();
        }
    }

    @Test
    void twoPhaseTransferIsInvisibleUntilCommitAndThenVisibleEverywhere() {
        TransactionOptions options = new TransactionOptions()
                .setTransactionType(TransactionType.TWO_PHASE)
                .setDurability(1)
                .setTimeout(1, TimeUnit.MINUTES);
        TransactionContext context = first.newTransactionContext(options);
        context.beginTransaction();
        LedgerEvent event;
        try {
            event = transfer(context, "alice", "bob", 120L);

            TransactionalMap<String, Long> txAccounts = context.getMap(ACCOUNTS);
            assertThat(txAccounts.get("alice")).isEqualTo(380L);
            assertThat(txAccounts.get("bob")).isEqualTo(320L);

            IMap<String, Long> committedAccounts = second.getMap(ACCOUNTS);
            assertThat(committedAccounts.get("alice")).isEqualTo(500L);
            assertThat(committedAccounts.get("bob")).isEqualTo(200L);
            assertThat(second.<LedgerEvent>getQueue(LEDGER_EVENTS)).isEmpty();
            assertThat(second.<String, String>getMultiMap(HISTORY).size()).isZero();

            context.commitTransaction();
        } catch (RuntimeException failure) {
            context.rollbackTransaction();
            throw failure;
        }

        IMap<String, Long> accounts = second.getMap(ACCOUNTS);
        assertThat(accounts.get("alice")).isEqualTo(380L);
        assertThat(accounts.get("bob")).isEqualTo(320L);
        assertThat(accounts.get("carol")).isEqualTo(50L);

        MultiMap<String, String> history = second.getMultiMap(HISTORY);
        assertThat(history.get("alice")).containsExactly("debit:120");
        assertThat(history.get("bob")).containsExactly("credit:120");

        IQueue<LedgerEvent> events = second.getQueue(LEDGER_EVENTS);
        assertThat(events).hasSize(1);
        LedgerEvent delivered = events.poll();
        assertThat(delivered).isNotNull();
        assertThat(delivered.from()).isEqualTo("alice");
        assertThat(delivered.to()).isEqualTo("bob");
        assertThat(delivered.amount()).isEqualTo(120L);
        assertThat(delivered.transferId()).isEqualTo(event.transferId());
        assertThat(second.getSet(AUDITED)).containsExactly(event.transferId());
    }

    @Test
    void rejectedTransferIsRolledBackWithoutTrace() {
        TransactionContext accepted = first.newTransactionContext();
        accepted.beginTransaction();
        transfer(accepted, "bob", "carol", 30L);
        accepted.commitTransaction();

        TransactionContext rejected = second.newTransactionContext();
        rejected.beginTransaction();
        assertThatThrownBy(() -> transfer(rejected, "carol", "alice", 1_000L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("carol");
        rejected.rollbackTransaction();

        IMap<String, Long> accounts = first.getMap(ACCOUNTS);
        assertThat(accounts.get("alice")).isEqualTo(500L);
        assertThat(accounts.get("bob")).isEqualTo(170L);
        assertThat(accounts.get("carol")).isEqualTo(80L);

        MultiMap<String, String> history = first.getMultiMap(HISTORY);
        assertThat(history.get("carol")).containsExactly("credit:30");
        assertThat(history.get("alice")).isEmpty();
        assertThat(first.<LedgerEvent>getQueue(LEDGER_EVENTS)).hasSize(1);
        assertThat(first.getSet(AUDITED)).hasSize(1);
    }

    @Test
    void pessimisticReadLocksTheAccountUntilTheTransactionEnds() throws InterruptedException {
        IMap<String, Long> accounts = second.getMap(ACCOUNTS);
        TransactionContext context = first.newTransactionContext();
        context.beginTransaction();
        try {
            TransactionalMap<String, Long> txAccounts = context.getMap(ACCOUNTS);
            Long balance = txAccounts.getForUpdate("carol");
            assertThat(balance).isEqualTo(50L);

            assertThat(accounts.isLocked("carol")).isTrue();
            assertThat(accounts.isLocked("alice")).isFalse();
            assertThat(accounts.tryPut("carol", 0L, 100, TimeUnit.MILLISECONDS)).isFalse();

            txAccounts.put("carol", balance + 25L);
            context.commitTransaction();
        } catch (RuntimeException failure) {
            context.rollbackTransaction();
            throw failure;
        }

        assertThat(accounts.isLocked("carol")).isFalse();
        assertThat(accounts.get("carol")).isEqualTo(75L);
        assertThat(accounts.tryPut("carol", 70L, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(accounts.get("carol")).isEqualTo(70L);
    }

    @Test
    void transactionalTaskCommitsOnReturnAndRollsBackOnFailure() {
        TransactionOptions onePhase = new TransactionOptions().setTransactionType(TransactionType.ONE_PHASE);

        LedgerEvent committed = second.executeTransaction(onePhase,
                context -> transferInTask(context, "alice", "carol", 75L));
        assertThat(committed.amount()).isEqualTo(75L);

        assertThatThrownBy(() -> second.executeTransaction(onePhase,
                context -> transferInTask(context, "bob", "alice", 999L)))
                .isInstanceOf(TransactionException.class)
                .hasCauseInstanceOf(InsufficientFundsException.class);

        IMap<String, Long> accounts = first.getMap(ACCOUNTS);
        assertThat(accounts.get("alice")).isEqualTo(425L);
        assertThat(accounts.get("bob")).isEqualTo(200L);
        assertThat(accounts.get("carol")).isEqualTo(125L);
        assertThat(first.<String, String>getMultiMap(HISTORY).get("bob")).isEmpty();

        IQueue<LedgerEvent> events = first.getQueue(LEDGER_EVENTS);
        assertThat(events).hasSize(1);
        assertThat(events.peek().transferId()).isEqualTo(committed.transferId());
        assertThat(first.getSet(AUDITED)).containsExactly(committed.transferId());
    }

    private static LedgerEvent transferInTask(TransactionalTaskContext context, String from, String to, long amount) {
        try {
            return transfer(context, from, to, amount);
        } catch (InsufficientFundsException rejected) {
            throw new TransactionException("Transfer rejected", rejected);
        }
    }

    /** Moves money between two accounts and records it, all inside the caller's transaction. */
    private static LedgerEvent transfer(TransactionalTaskContext context, String from, String to, long amount) {
        TransactionalMap<String, Long> accounts = context.getMap(ACCOUNTS);
        long fromBalance = accounts.getForUpdate(from);
        long toBalance = accounts.getForUpdate(to);
        LedgerEvent event = new LedgerEvent(UUID.randomUUID().toString(), from, to, amount);

        TransactionalSet<String> audited = context.getSet(AUDITED);
        audited.add(event.transferId());
        if (fromBalance < amount) {
            throw new InsufficientFundsException(from + " has " + fromBalance + " but needs " + amount);
        }
        accounts.put(from, fromBalance - amount);
        accounts.put(to, toBalance + amount);

        TransactionalMultiMap<String, String> history = context.getMultiMap(HISTORY);
        history.put(from, "debit:" + amount);
        history.put(to, "credit:" + amount);

        TransactionalQueue<LedgerEvent> events = context.getQueue(LEDGER_EVENTS);
        assertThat(events.offer(event)).isTrue();
        return event;
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

    /** Raised by the application when an account cannot cover a transfer. */
    static final class InsufficientFundsException extends RuntimeException {

        InsufficientFundsException(String message) {
            super(message);
        }
    }

    /** A completed transfer, queued for the auditor. */
    record LedgerEvent(String transferId, String from, String to, long amount) implements Serializable {
    }
}
