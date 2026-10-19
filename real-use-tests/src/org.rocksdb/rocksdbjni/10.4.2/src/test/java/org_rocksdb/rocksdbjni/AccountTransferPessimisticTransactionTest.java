/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

/**
 * Pessimistic transactions on a `TransactionDB`: row locks taken with `getForUpdate`, commit
 * and rollback, savepoints, lock-timeout conflicts between concurrent transactions, and
 * snapshot write-conflict detection.
 *
 * A small bank keeps account balances as decimal strings. A transfer locks both accounts,
 * checks the balance, and either commits both updates or rolls them back, so the total money
 * in the bank never changes. The lock timeout is zero, so a transaction that finds a row
 * already locked fails at once instead of waiting, which keeps every conflict deterministic.
 */
public class AccountTransferPessimisticTransactionTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void commitsTransfersRejectsOverdraftsAndKeepsBalancesAfterRestart() throws Exception {
        String path = dataDir.resolve("bank").toString();

        try (Bank bank = Bank.open(path)) {
            bank.openAccounts(Map.of("alice", 500L, "bob", 100L, "carol", 0L));

            assertTrue(bank.transfer("alice", "bob", 200));
            assertTrue(bank.transfer("bob", "carol", 250));
            assertFalse(bank.transfer("carol", "alice", 1_000), "an overdraft is rolled back");

            assertEquals(300L, bank.balance("alice"));
            assertEquals(50L, bank.balance("bob"));
            assertEquals(250L, bank.balance("carol"));
        }

        try (Bank bank = Bank.open(path)) {
            assertEquals(300L, bank.balance("alice"));
            assertEquals(50L, bank.balance("bob"));
            assertEquals(250L, bank.balance("carol"));
            assertEquals(600L, bank.balance("alice") + bank.balance("bob") + bank.balance("carol"));
        }
    }

    @Test
    void failsAConflictingTransactionWhileAnotherHoldsTheRowLock() throws Exception {
        String path = dataDir.resolve("locks").toString();

        try (Bank bank = Bank.open(path)) {
            bank.openAccounts(Map.of("alice", 500L, "bob", 100L));

            try (Transaction first = bank.begin(); ReadOptions read = new ReadOptions()) {
                byte[] locked = first.getForUpdate(read, bytes("alice"), true);
                assertEquals("500", text(locked));
                first.put(bytes("alice"), bytes("450"));

                try (Transaction second = bank.begin()) {
                    RocksDBException conflict = assertThrows(RocksDBException.class,
                            () -> second.getForUpdate(read, bytes("alice"), true));
                    assertNotNull(conflict.getStatus());
                    assertEquals(Status.Code.TimedOut, conflict.getStatus().getCode());

                    byte[] unlocked = second.getForUpdate(read, bytes("bob"), true);
                    assertEquals("100", text(unlocked), "rows nobody holds stay available");
                    second.rollback();
                }

                assertEquals(500L, bank.balance("alice"), "uncommitted writes stay private");
                assertEquals("450", text(first.get(read, bytes("alice"))));
                first.commit();
            }

            assertEquals(450L, bank.balance("alice"));
            assertTrue(bank.transfer("alice", "bob", 50), "the lock is released on commit");
            assertEquals(400L, bank.balance("alice"));
            assertEquals(150L, bank.balance("bob"));
        }
    }

    @Test
    void rollsBackToASavePointAndDetectsWriteConflictsAgainstASnapshot() throws Exception {
        String path = dataDir.resolve("savepoints").toString();

        try (Bank bank = Bank.open(path)) {
            bank.openAccounts(Map.of("alice", 500L, "bob", 100L));

            try (Transaction batch = bank.begin(); ReadOptions read = new ReadOptions()) {
                batch.put(bytes("dave"), bytes("75"));
                batch.setSavePoint();
                batch.put(bytes("erin"), bytes("25"));
                batch.delete(bytes("bob"));
                assertEquals(3, batch.getNumKeys());

                batch.rollbackToSavePoint();
                assertNull(batch.get(read, bytes("erin")));
                assertEquals("100", text(batch.get(read, bytes("bob"))));
                batch.commit();
            }

            assertEquals(75L, bank.balance("dave"));
            assertEquals(100L, bank.balance("bob"));
            assertNull(bank.db.get(bytes("erin")), "writes after the savepoint never commit");

            try (Transaction audit = bank.begin(); ReadOptions read = new ReadOptions()) {
                audit.setSnapshot();
                assertNotNull(audit.getSnapshot());

                bank.db.put(bytes("alice"), bytes("999"));

                RocksDBException conflict = assertThrows(RocksDBException.class,
                        () -> audit.getForUpdate(read, bytes("alice"), true));
                assertEquals(Status.Code.Busy, conflict.getStatus().getCode());
                assertArrayEquals(bytes("100"), audit.getForUpdate(read, bytes("bob"), true));
                audit.rollback();
            }

            assertEquals(999L, bank.balance("alice"));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** The application's account store: one `TransactionDB` keyed by account name. */
    private static final class Bank implements AutoCloseable {

        private final Options options;
        private final TransactionDBOptions transactionOptions;
        private final WriteOptions writeOptions;
        private final TransactionDB db;

        private Bank(Options options, TransactionDBOptions transactionOptions,
                WriteOptions writeOptions, TransactionDB db) {
            this.options = options;
            this.transactionOptions = transactionOptions;
            this.writeOptions = writeOptions;
            this.db = db;
        }

        static Bank open(String path) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true);
            TransactionDBOptions transactionOptions = new TransactionDBOptions()
                    .setTransactionLockTimeout(0);
            WriteOptions writeOptions = new WriteOptions().setSync(true);
            TransactionDB db = TransactionDB.open(options, transactionOptions, path);
            return new Bank(options, transactionOptions, writeOptions, db);
        }

        Transaction begin() {
            return db.beginTransaction(writeOptions);
        }

        void openAccounts(Map<String, Long> balances) throws RocksDBException {
            try (Transaction txn = begin()) {
                for (Map.Entry<String, Long> account : balances.entrySet()) {
                    txn.put(bytes(account.getKey()), bytes(Long.toString(account.getValue())));
                }
                txn.commit();
            }
        }

        boolean transfer(String from, String to, long amount) throws RocksDBException {
            try (Transaction txn = begin(); ReadOptions read = new ReadOptions()) {
                long fromBalance = Long.parseLong(text(txn.getForUpdate(read, bytes(from), true)));
                long toBalance = Long.parseLong(text(txn.getForUpdate(read, bytes(to), true)));
                if (fromBalance < amount) {
                    txn.rollback();
                    return false;
                }
                txn.put(bytes(from), bytes(Long.toString(fromBalance - amount)));
                txn.put(bytes(to), bytes(Long.toString(toBalance + amount)));
                txn.commit();
                return true;
            }
        }

        long balance(String account) throws RocksDBException {
            byte[] value = db.get(bytes(account));
            assertNotNull(value, "unknown account " + account);
            return Long.parseLong(text(value));
        }

        @Override
        public void close() {
            db.close();
            writeOptions.close();
            transactionOptions.close();
            options.close();
        }
    }
}
