/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.AbstractWalFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WalFilter.LogRecordFoundResult;
import org.rocksdb.WalProcessingOption;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * Write-ahead log recovery filtering: an `AbstractWalFilter` installed with
 * `DBOptions.setWalFilter` inspects every batch RocksDB replays from the WAL while opening, and
 * rewrites it, drops it with `IGNORE_CURRENT_RECORD`, or ends recovery with `STOP_REPLAY`.
 *
 * A point-of-sale service journals card payments in the default column family and its
 * settlement cursor in a `checkpoints` family, tagging every payment batch with the terminal
 * that sent it through `putLogData`. After an unclean shutdown it reopens the journal with a
 * recovery filter that masks card numbers before they reach a table file, discards payments
 * from a quarantined test terminal, and stops replaying at a maintenance cutoff marker.
 */
public class PaymentJournalWalRecoveryFilterTest {

    private static final String CHECKPOINTS = "checkpoints";
    private static final String QUARANTINED_TERMINAL = "pos-test";
    private static final String CUTOFF = "maintenance-cutoff";

    @TempDir
    Path dataDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void recoveryFilterMasksDropsAndStopsReplayedBatches() throws Exception {
        Path path = dataDir.resolve("journal");

        try (Journal journal = Journal.open(path, null)) {
            journal.record("pos-12", "pay-1", "card=4111111111111111;amount=10.00");
            journal.record(QUARANTINED_TERMINAL, "pay-2", "card=4000000000000002;amount=99.99");
            journal.record("pos-12", "pay-3", "card=5500000000000004;amount=25.50",
                    "pay-3-tip", "card=5500000000000004;amount=4.50");
            journal.settle("pay-3");
            journal.record(CUTOFF, "pay-4", "card=378282246310005;amount=7.25");
            journal.record("pos-12", "pay-5", "card=6011111111111117;amount=60.00");
            assertEquals(6, journal.payments().size(), "every payment is readable before the crash");
        }

        try (RecoveryFilter filter = new RecoveryFilter()) {
            try (Journal journal = Journal.open(path, filter)) {
                assertEquals(Map.of(
                        "pay-1", "card=************1111;amount=10.00",
                        "pay-3", "card=************0004;amount=25.50",
                        "pay-3-tip", "card=************0004;amount=4.50"), journal.payments());
                assertEquals("pay-3", journal.settledThrough());
            }

            assertEquals(Map.of("default", 0, CHECKPOINTS, 1), filter.familyIds,
                    "the filter learns every family RocksDB recovers");
            assertEquals(Set.of(0, 1), filter.familyLogNumbers.keySet());
            assertEquals(List.of(
                    "pos-12 put default/pay-1 -> masked",
                    "pos-test put default/pay-2 -> ignored",
                    "pos-12 put default/pay-3 put default/pay-3-tip -> masked",
                    "untagged put checkpoints/settled -> unchanged",
                    "maintenance-cutoff put default/pay-4 -> stopped"), filter.decisions,
                    "replay stops at the cutoff, so the batch after it is never offered");
            assertTrue(filter.walFiles.stream().allMatch(name -> name.endsWith(".log")),
                    "every replayed batch names the WAL file it came from: " + filter.walFiles);
        }

        try (Journal journal = Journal.open(path, null)) {
            assertEquals(Set.of("pay-1", "pay-3", "pay-3-tip"), journal.payments().keySet(),
                    "the filtered recovery was flushed, so discarded batches never come back");
            assertEquals("card=************1111;amount=10.00", journal.payments().get("pay-1"));
            journal.record("pos-12", "pay-6", "card=4242424242424242;amount=15.00");
        }

        try (Journal journal = Journal.open(path, null)) {
            assertEquals("card=4242424242424242;amount=15.00", journal.payments().get("pay-6"),
                    "a journal reopened without a filter replays its WAL unchanged");
            assertNull(journal.payments().get("pay-5"));
        }
    }

    @Test
    void flushedJournalOffersNothingToTheFilter() throws Exception {
        Path path = dataDir.resolve("flushed");

        try (Journal journal = Journal.open(path, null)) {
            journal.record("pos-7", "pay-1", "card=4111111111111111;amount=3.00");
            journal.settle("pay-1");
            journal.flush();
        }

        try (RecoveryFilter filter = new RecoveryFilter();
                Journal journal = Journal.open(path, filter)) {
            assertEquals(List.of(), filter.decisions, "flushed data is read from table files");
            assertEquals("card=4111111111111111;amount=3.00", journal.payments().get("pay-1"),
                    "the filter only sees replayed batches, never data already in table files");
            assertEquals("pay-1", journal.settledThrough());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** The payment journal: payments in the default family and the settlement cursor beside it. */
    private static final class Journal implements AutoCloseable {

        private final DBOptions options;
        private final ColumnFamilyOptions familyOptions;
        private final List<ColumnFamilyHandle> handles;
        private final RocksDB db;

        private Journal(DBOptions options, ColumnFamilyOptions familyOptions,
                List<ColumnFamilyHandle> handles, RocksDB db) {
            this.options = options;
            this.familyOptions = familyOptions;
            this.handles = handles;
            this.db = db;
        }

        /** Opens the journal, replaying its WAL through `filter` when one is given. */
        static Journal open(Path path, AbstractWalFilter filter) throws RocksDBException {
            DBOptions options = new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true)
                    // Leave unflushed batches in the WAL on close, as a crash would.
                    .setAvoidFlushDuringShutdown(true);
            if (filter != null) {
                options.setWalFilter(filter);
            }
            ColumnFamilyOptions familyOptions = new ColumnFamilyOptions();
            List<ColumnFamilyDescriptor> descriptors = List.of(
                    new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, familyOptions),
                    new ColumnFamilyDescriptor(bytes(CHECKPOINTS), familyOptions));
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            RocksDB db = RocksDB.open(options, path.toString(), descriptors, handles);
            return new Journal(options, familyOptions, handles, db);
        }

        /** Journals the given `id, details` pairs as one batch tagged with the terminal. */
        void record(String terminal, String... payments) throws RocksDBException {
            try (WriteBatch batch = new WriteBatch();
                    WriteOptions write = new WriteOptions()) {
                batch.putLogData(bytes(terminal));
                for (int i = 0; i < payments.length; i += 2) {
                    batch.put(bytes(payments[i]), bytes(payments[i + 1]));
                }
                db.write(write, batch);
            }
        }

        void settle(String paymentId) throws RocksDBException {
            db.put(handles.get(1), bytes("settled"), bytes(paymentId));
        }

        String settledThrough() throws RocksDBException {
            byte[] value = db.get(handles.get(1), bytes("settled"));
            return value == null ? null : text(value);
        }

        Map<String, String> payments() {
            Map<String, String> payments = new TreeMap<>();
            try (RocksIterator iterator = db.newIterator(handles.get(0))) {
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    payments.put(text(iterator.key()), text(iterator.value()));
                }
            }
            return payments;
        }

        void flush() throws RocksDBException {
            try (FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flush, handles);
            }
        }

        @Override
        public void close() {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
            db.close();
            familyOptions.close();
            options.close();
        }
    }

    /** Masks card numbers, drops quarantined payments, and halts replay at a cutoff marker. */
    private static final class RecoveryFilter extends AbstractWalFilter {

        final Map<String, Integer> familyIds = new TreeMap<>();
        final Map<Integer, Long> familyLogNumbers = new TreeMap<>();
        final List<String> decisions = new ArrayList<>();
        final List<String> walFiles = new ArrayList<>();

        @Override
        public String name() {
            return "payment-journal-recovery";
        }

        @Override
        public void columnFamilyLogNumberMap(Map<Integer, Long> cfLognumber,
                Map<String, Integer> cfNameId) {
            familyLogNumbers.putAll(cfLognumber);
            familyIds.putAll(cfNameId);
        }

        @Override
        public LogRecordFoundResult logRecordFound(long logNumber, String logFileName,
                WriteBatch batch, WriteBatch newBatch) {
            walFiles.add(logFileName);
            BatchReader reader = new BatchReader();
            try {
                batch.iterate(reader);
            } catch (RocksDBException e) {
                return new LogRecordFoundResult(WalProcessingOption.CORRUPTED_RECORD, false);
            } finally {
                reader.close();
            }
            String summary = reader.terminal + " " + String.join(" ", reader.changes);

            if (QUARANTINED_TERMINAL.equals(reader.terminal)) {
                decisions.add(summary + " -> ignored");
                return new LogRecordFoundResult(WalProcessingOption.IGNORE_CURRENT_RECORD, false);
            }
            if (CUTOFF.equals(reader.terminal)) {
                decisions.add(summary + " -> stopped");
                return new LogRecordFoundResult(WalProcessingOption.STOP_REPLAY, false);
            }
            if (reader.payments.isEmpty()) {
                decisions.add(summary + " -> unchanged");
                return LogRecordFoundResult.CONTINUE_UNCHANGED;
            }
            try {
                newBatch.putLogData(bytes(reader.terminal));
                for (Map.Entry<String, String> payment : reader.payments.entrySet()) {
                    newBatch.put(bytes(payment.getKey()), bytes(mask(payment.getValue())));
                }
            } catch (RocksDBException e) {
                return new LogRecordFoundResult(WalProcessingOption.CORRUPTED_RECORD, false);
            }
            decisions.add(summary + " -> masked");
            return new LogRecordFoundResult(WalProcessingOption.CONTINUE_PROCESSING, true);
        }

        /** Keeps the last four digits of the card number in `card=<digits>;amount=<amount>`. */
        private static String mask(String details) {
            int start = details.indexOf('=') + 1;
            int end = details.indexOf(';');
            String card = details.substring(start, end);
            String hidden = "*".repeat(card.length() - 4) + card.substring(card.length() - 4);
            return details.substring(0, start) + hidden + details.substring(end);
        }

        /** Reads the terminal tag and the payments out of one replayed batch. */
        private static final class BatchReader extends WriteBatch.Handler {

            private static final List<String> FAMILIES = List.of("default", CHECKPOINTS);

            String terminal = "untagged";
            final List<String> changes = new ArrayList<>();
            final Map<String, String> payments = new TreeMap<>();

            @Override
            public void logData(byte[] blob) {
                terminal = text(blob);
            }

            @Override
            public void put(int columnFamilyId, byte[] key, byte[] value) {
                changes.add("put " + FAMILIES.get(columnFamilyId) + "/" + text(key));
                if (columnFamilyId == 0) {
                    payments.put(text(key), text(value));
                }
            }

            @Override
            public void put(byte[] key, byte[] value) {
                put(0, key, value);
            }

            @Override
            public void merge(int columnFamilyId, byte[] key, byte[] value) {
                throw new IllegalStateException("the journal never merges");
            }

            @Override
            public void merge(byte[] key, byte[] value) {
                throw new IllegalStateException("the journal never merges");
            }

            @Override
            public void delete(int columnFamilyId, byte[] key) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void delete(byte[] key) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void singleDelete(int columnFamilyId, byte[] key) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void singleDelete(byte[] key) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void deleteRange(int columnFamilyId, byte[] beginKey, byte[] endKey) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void deleteRange(byte[] beginKey, byte[] endKey) {
                throw new IllegalStateException("the journal never deletes");
            }

            @Override
            public void putBlobIndex(int columnFamilyId, byte[] key, byte[] value) {
                throw new IllegalStateException("the journal does not use blob files");
            }

            // The journal writes plain batches, so two-phase-commit markers never appear.
            @Override
            public void markBeginPrepare() {
            }

            @Override
            public void markEndPrepare(byte[] xid) {
            }

            @Override
            public void markNoop(boolean emptyBatch) {
            }

            @Override
            public void markRollback(byte[] xid) {
            }

            @Override
            public void markCommit(byte[] xid) {
            }

            @Override
            public void markCommitWithTimestamp(byte[] xid, byte[] timestamp) {
            }
        }
    }
}
