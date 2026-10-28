/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.OptimisticTransactionOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.Status;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

/**
 * Optimistic transactions on an `OptimisticTransactionDB`: lock-free transactions that buffer
 * their writes, read their own uncommitted changes, and are validated for write conflicts only
 * when they commit.
 *
 * A concert box office books seats without holding locks. Each booking reads the seat with
 * `getForUpdate`, writes the holder, and commits; a booking that lost the race fails at commit
 * with a `Busy` status and is retried in a recycled transaction. Price reviews use a
 * transaction snapshot so that any change made after the review started invalidates it, and
 * group bookings use savepoints to drop a seat that turned out to be unavailable.
 */
public class SeatReservationOptimisticTransactionTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void bookingReadsItsOwnWritesAndPublishesThemOnCommit() throws Exception {
        try (BoxOffice office = BoxOffice.open(workDir.resolve("own-writes").toString())) {
            office.release("seat-A1", "free");
            office.release("seat-A2", "free");
            office.release("seat-A3", "free");

            try (Transaction booking = office.db.beginTransaction(office.writeOptions);
                    ReadOptions read = new ReadOptions()) {
                assertEquals(Transaction.TransactionState.STARTED, booking.getState());
                assertEquals("free", text(booking.getForUpdate(read, bytes("seat-A2"), true)));
                booking.put(bytes("seat-A2"), bytes("held:alice"));
                booking.delete(bytes("seat-A3"));
                booking.put(bytes("seat-A4"), bytes("held:alice"));

                assertEquals("held:alice", text(booking.get(read, bytes("seat-A2"))),
                        "the transaction reads its own buffered write");
                assertNull(booking.get(read, bytes("seat-A3")),
                        "the transaction sees its own buffered delete");
                assertEquals("free", office.status("seat-A2"),
                        "other readers do not see uncommitted writes");
                assertEquals("free", office.status("seat-A3"));
                assertNull(office.status("seat-A4"));

                Map<String, String> view = new LinkedHashMap<>();
                try (RocksIterator seats = booking.getIterator(read)) {
                    for (seats.seekToFirst(); seats.isValid(); seats.next()) {
                        view.put(text(seats.key()), text(seats.value()));
                    }
                }
                assertEquals(Map.of("seat-A1", "free", "seat-A2", "held:alice",
                        "seat-A4", "held:alice"), view,
                        "the transaction iterator merges buffered writes over committed data");
                assertEquals(2L, booking.getNumPuts());
                assertEquals(1L, booking.getNumDeletes());

                booking.commit();
            }
            assertEquals("held:alice", office.status("seat-A2"));
            assertNull(office.status("seat-A3"));
            assertEquals("held:alice", office.status("seat-A4"));
            assertEquals("free", office.status("seat-A1"));
        }
    }

    @Test
    void competingBookingFailsAtCommitAndRetriesInRecycledTransaction() throws Exception {
        try (BoxOffice office = BoxOffice.open(workDir.resolve("conflict").toString())) {
            office.release("seat-B7", "free");
            office.release("seat-B8", "free");

            try (ReadOptions read = new ReadOptions();
                    Transaction alice = office.db.beginTransaction(office.writeOptions);
                    Transaction bob = office.db.beginTransaction(office.writeOptions)) {
                assertEquals("free", text(alice.getForUpdate(read, bytes("seat-B7"), true)));
                assertEquals("free", text(bob.getForUpdate(read, bytes("seat-B7"), true)),
                        "optimistic transactions never block each other while running");
                alice.put(bytes("seat-B7"), bytes("held:alice"));
                bob.put(bytes("seat-B7"), bytes("held:bob"));

                alice.commit();
                RocksDBException conflict = assertThrows(RocksDBException.class, bob::commit,
                        "the later commit detects the seat was written after it read it");
                assertNotNull(conflict.getStatus());
                assertEquals(Status.Code.Busy, conflict.getStatus().getCode());
                assertEquals("held:alice", office.status("seat-B7"),
                        "the failed booking left no trace");

                bob.rollback();
                Transaction retry = office.db.beginTransaction(office.writeOptions, bob);
                assertSame(bob, retry, "the box office recycles the native transaction");
                assertEquals("held:alice", text(retry.getForUpdate(read, bytes("seat-B7"), true)));
                assertEquals("free", text(retry.getForUpdate(read, bytes("seat-B8"), true)));
                retry.put(bytes("seat-B8"), bytes("held:bob"));
                retry.commit();
            }
            assertEquals("held:alice", office.status("seat-B7"));
            assertEquals("held:bob", office.status("seat-B8"));
        }
    }

    @Test
    void plainReadsAreNotValidatedButTrackedWritesAre() throws Exception {
        try (BoxOffice office = BoxOffice.open(workDir.resolve("tracking").toString())) {
            office.release("seat-C1", "free");
            office.release("seat-C2", "free");

            try (ReadOptions read = new ReadOptions();
                    Transaction lookup = office.db.beginTransaction(office.writeOptions)) {
                assertEquals("free", text(lookup.get(read, bytes("seat-C1"))));
                office.release("seat-C1", "held:carol");
                lookup.put(bytes("seat-C2"), bytes("held:dave"));
                lookup.commit();
            }
            assertEquals("held:carol", office.status("seat-C1"),
                    "a plain get does not make the transaction conflict on that key");
            assertEquals("held:dave", office.status("seat-C2"));

            try (Transaction blind = office.db.beginTransaction(office.writeOptions)) {
                blind.put(bytes("seat-C2"), bytes("held:erin"));
                office.release("seat-C2", "free");
                RocksDBException conflict = assertThrows(RocksDBException.class, blind::commit,
                        "a key written by the transaction is validated at commit");
                assertEquals(Status.Code.Busy, conflict.getStatus().getCode());
            }
            assertEquals("free", office.status("seat-C2"));
        }
    }

    @Test
    void snapshotMakesEveryLaterChangeAConflict() throws Exception {
        try (BoxOffice office = BoxOffice.open(workDir.resolve("snapshot").toString())) {
            office.release("price-D", "120");

            try (OptimisticTransactionOptions noSnapshot = new OptimisticTransactionOptions();
                    Transaction review = office.db.beginTransaction(office.writeOptions,
                            noSnapshot)) {
                office.release("price-D", "125");
                review.put(bytes("price-D"), bytes("130"));
                review.commit();
            }
            assertEquals("130", office.status("price-D"),
                    "without a snapshot, changes made before the key was touched are accepted");

            try (OptimisticTransactionOptions withSnapshot = new OptimisticTransactionOptions()
                            .setSetSnapshot(true);
                    Transaction review = office.db.beginTransaction(office.writeOptions,
                            withSnapshot);
                    ReadOptions atStart = new ReadOptions()) {
                Snapshot started = review.getSnapshot();
                assertNotNull(started, "the transaction pinned a snapshot when it began");
                atStart.setSnapshot(started);

                office.release("price-D", "140");
                assertEquals("130", text(review.get(atStart, bytes("price-D"))),
                        "reads through the transaction snapshot ignore the later change");
                assertEquals("140", office.status("price-D"));

                review.put(bytes("price-D"), bytes("135"));
                RocksDBException conflict = assertThrows(RocksDBException.class, review::commit,
                        "the price changed after the snapshot, so the review is stale");
                assertEquals(Status.Code.Busy, conflict.getStatus().getCode());
            }
            assertEquals("140", office.status("price-D"));
        }
    }

    @Test
    void groupBookingDropsUnavailableSeatWithSavepoint() throws Exception {
        try (BoxOffice office = BoxOffice.open(workDir.resolve("savepoint").toString())) {
            office.release("seat-E1", "free");
            office.release("seat-E2", "free");
            office.release("seat-E3", "held:frank");

            try (ReadOptions read = new ReadOptions();
                    Transaction group = office.db.beginTransaction(office.writeOptions)) {
                for (String seat : new String[] {"seat-E1", "seat-E2"}) {
                    assertEquals("free", text(group.getForUpdate(read, bytes(seat), true)));
                    group.put(bytes(seat), bytes("held:grace"));
                }
                group.setSavePoint();
                group.put(bytes("seat-E3"), bytes("held:grace"));
                assertEquals("held:grace", text(group.get(read, bytes("seat-E3"))));
                assertEquals("held:frank", office.status("seat-E3"),
                        "the tentative hold collides with a committed booking");
                group.rollbackToSavePoint();
                assertEquals("held:frank", text(group.get(read, bytes("seat-E3"))),
                        "rolling back to the savepoint undid only the third seat");
                assertEquals("held:grace", text(group.get(read, bytes("seat-E2"))));
                group.commit();
            }
            assertEquals("held:grace", office.status("seat-E1"));
            assertEquals("held:grace", office.status("seat-E2"));
            assertEquals("held:frank", office.status("seat-E3"));

            try (Transaction cancelled = office.db.beginTransaction(office.writeOptions)) {
                cancelled.put(bytes("seat-E1"), bytes("free"));
                cancelled.rollback();
            }
            assertEquals("held:grace", office.status("seat-E1"),
                    "a rolled-back transaction writes nothing");
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    /** The box office database: an `OptimisticTransactionDB` with direct reads and writes. */
    private static final class BoxOffice implements AutoCloseable {

        private final Options options;
        private final WriteOptions writeOptions;
        private final OptimisticTransactionDB db;

        private BoxOffice(Options options, WriteOptions writeOptions, OptimisticTransactionDB db) {
            this.options = options;
            this.writeOptions = writeOptions;
            this.db = db;
        }

        static BoxOffice open(String path) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true);
            return new BoxOffice(options, new WriteOptions(),
                    OptimisticTransactionDB.open(options, path));
        }

        /** Writes a seat or price outside any transaction, as an administrator would. */
        void release(String key, String value) throws RocksDBException {
            db.put(writeOptions, bytes(key), bytes(value));
        }

        String status(String key) throws RocksDBException {
            return text(db.get(bytes(key)));
        }

        @Override
        public void close() {
            db.close();
            writeOptions.close();
            options.close();
        }
    }
}
