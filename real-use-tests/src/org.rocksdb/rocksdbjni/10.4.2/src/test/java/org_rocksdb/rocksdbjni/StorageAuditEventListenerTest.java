/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.AbstractEventListener;
import org.rocksdb.CompactionJobInfo;
import org.rocksdb.CompactionJobStats;
import org.rocksdb.CompactionReason;
import org.rocksdb.FlushJobInfo;
import org.rocksdb.FlushOptions;
import org.rocksdb.FlushReason;
import org.rocksdb.MemTableInfo;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.Status;
import org.rocksdb.TableFileCreationInfo;
import org.rocksdb.TableFileCreationReason;
import org.rocksdb.TableFileDeletionInfo;

/**
 * Event listeners: an `AbstractEventListener` registered through `Options.setListeners` that
 * RocksDB calls back from its background threads when a memtable is sealed, a flush begins and
 * completes, a table file is created or deleted, and a compaction begins and completes.
 *
 * A storage operations dashboard keeps an audit trail of what the embedded database does on
 * disk, so that operators can see which flushes and compactions ran, why they ran, and which
 * SST files they wrote and removed.
 */
public class StorageAuditEventListenerTest {

    private static final long WAIT_SECONDS = 30;

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void auditsManualFlushOfTheMemtable() throws Exception {
        String path = workDir.resolve("orders").toString();
        AuditListener audit = new AuditListener(1, 0);

        try (Options options = new Options()
                .setCreateIfMissing(true)
                .setListeners(List.of(audit));
                RocksDB db = RocksDB.open(options, path);
                FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
            for (int i = 0; i < 25; i++) {
                db.put(bytes("order-" + i), bytes("pending"));
            }
            db.delete(bytes("order-3"));
            db.flush(flush);

            assertTrue(audit.flushesCompleted.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "the flush completion is reported");

            assertEquals(1, audit.sealedMemtables.size(), "the flushed memtable is sealed first");
            SealedMemtable sealed = audit.sealedMemtables.peek();
            assertEquals("default", sealed.columnFamily());
            assertEquals(26L, sealed.entries(), "puts and deletes are counted as entries");
            assertEquals(1L, sealed.deletes());

            assertEquals(1, audit.flushesStarted.size(), "the flush begin is reported");
            assertEquals(1, audit.flushes.size());
            FlushRecord record = audit.flushes.peek();
            assertEquals("default", record.columnFamily());
            assertEquals(FlushReason.MANUAL_FLUSH, record.reason());
            assertEquals(25L, record.entries(), "the shadowed put of order-3 is dropped on flush");
            assertTrue(record.largestSeqno() >= record.smallestSeqno());
            assertTrue(record.filePath().endsWith(".sst"), record.filePath());
            assertTrue(Files.exists(Path.of(record.filePath())), "the flushed file is on disk");

            List<CreatedFile> created = new ArrayList<>(audit.createdFiles);
            assertEquals(1, created.size());
            CreatedFile file = created.get(0);
            assertEquals(TableFileCreationReason.FLUSH, file.reason());
            assertEquals(record.filePath(), file.filePath());
            assertEquals(Status.Code.Ok, file.status());
            assertEquals(Files.size(Path.of(file.filePath())), file.size());
            assertEquals(25L, file.entries(), "24 live values and one tombstone");

            assertTrue(audit.compactions.isEmpty(), "a single file needs no compaction");
            assertEquals("pending", string(db.get(bytes("order-24"))));
        }
        audit.close();
    }

    @Test
    void auditsManualCompactionThatReplacesFlushedFiles() throws Exception {
        String path = workDir.resolve("inventory").toString();
        AuditListener audit = new AuditListener(3, 1);

        try (Options options = new Options()
                .setCreateIfMissing(true)
                .setDisableAutoCompactions(true)
                .setListeners(List.of(audit));
                RocksDB db = RocksDB.open(options, path);
                FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
            for (String sku : List.of("apple", "banana", "cherry", "date", "elder")) {
                db.put(bytes(sku), bytes("10"));
            }
            db.flush(flush);
            for (String sku : List.of("apple", "banana", "cherry", "date", "elder")) {
                db.put(bytes(sku), bytes("7"));
            }
            db.flush(flush);
            db.delete(bytes("cherry"));
            db.flush(flush);

            assertTrue(audit.flushesCompleted.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "all three flushes are reported");
            Set<String> flushedFiles = new HashSet<>();
            for (FlushRecord record : audit.flushes) {
                flushedFiles.add(record.filePath());
            }
            assertEquals(3, flushedFiles.size(), "each flush writes its own file");

            db.compactRange();
            assertTrue(audit.compactionsCompleted.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "the compaction completion is reported");

            assertEquals(1, audit.compactionsStarted.size(), "the compaction begin is reported");
            assertEquals(1, audit.compactions.size());
            CompactionRecord compaction = audit.compactions.peek();
            assertEquals("default", compaction.columnFamily());
            assertEquals(CompactionReason.kManualCompaction, compaction.reason());
            assertEquals(Status.Code.Ok, compaction.status());
            assertEquals(flushedFiles, new HashSet<>(compaction.inputFiles()));
            assertEquals(1, compaction.outputFiles().size());
            assertEquals(11L, compaction.inputRecords(), "two versions per key and one delete");
            assertEquals(4L, compaction.outputRecords(), "only the latest live values survive");

            String output = compaction.outputFiles().get(0);
            assertTrue(Files.exists(Path.of(output)), "the compacted file is on disk");
            assertTrue(audit.createdFiles.stream().anyMatch(file ->
                    file.filePath().equals(output)
                            && file.reason() == TableFileCreationReason.COMPACTION
                            && file.entries() == 4L), "the compaction output file is reported");

            assertTrue(poll(() -> deletedPaths(audit).containsAll(flushedFiles)),
                    "the obsolete input files are reported as deleted");
            for (String input : flushedFiles) {
                assertFalse(Files.exists(Path.of(input)), "the input file is removed: " + input);
            }

            assertEquals("7", string(db.get(bytes("apple"))));
            assertNull(db.get(bytes("cherry")));
        }
        audit.close();
    }

    private static Set<String> deletedPaths(AuditListener audit) {
        Set<String> paths = new HashSet<>();
        for (DeletedFile file : audit.deletedFiles) {
            if (file.status() == Status.Code.Ok) {
                paths.add(file.filePath());
            }
        }
        return paths;
    }

    private static boolean poll(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.get();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static Status.Code code(Status status) {
        return status == null ? Status.Code.Ok : status.getCode();
    }

    /// Records every storage event as an immutable value, since the callback arguments
    /// belong to RocksDB and are only valid while the callback runs.
    static final class AuditListener extends AbstractEventListener {

        final Queue<SealedMemtable> sealedMemtables = new ConcurrentLinkedQueue<>();
        final Queue<String> flushesStarted = new ConcurrentLinkedQueue<>();
        final Queue<FlushRecord> flushes = new ConcurrentLinkedQueue<>();
        final Queue<CreatedFile> createdFiles = new ConcurrentLinkedQueue<>();
        final Queue<DeletedFile> deletedFiles = new ConcurrentLinkedQueue<>();
        final Queue<Integer> compactionsStarted = new ConcurrentLinkedQueue<>();
        final Queue<CompactionRecord> compactions = new ConcurrentLinkedQueue<>();
        final CountDownLatch flushesCompleted;
        final CountDownLatch compactionsCompleted;

        AuditListener(int expectedFlushes, int expectedCompactions) {
            super(EnabledEventCallback.ON_MEMTABLE_SEALED,
                    EnabledEventCallback.ON_FLUSH_BEGIN,
                    EnabledEventCallback.ON_FLUSH_COMPLETED,
                    EnabledEventCallback.ON_TABLE_FILE_CREATED,
                    EnabledEventCallback.ON_TABLE_FILE_DELETED,
                    EnabledEventCallback.ON_COMPACTION_BEGIN,
                    EnabledEventCallback.ON_COMPACTION_COMPLETED);
            this.flushesCompleted = new CountDownLatch(expectedFlushes);
            this.compactionsCompleted = new CountDownLatch(expectedCompactions);
        }

        @Override
        public void onMemTableSealed(MemTableInfo info) {
            sealedMemtables.add(new SealedMemtable(info.getColumnFamilyName(),
                    info.getNumEntries(), info.getNumDeletes()));
        }

        @Override
        public void onFlushBegin(RocksDB db, FlushJobInfo info) {
            flushesStarted.add(info.getColumnFamilyName());
        }

        @Override
        public void onFlushCompleted(RocksDB db, FlushJobInfo info) {
            flushes.add(new FlushRecord(info.getColumnFamilyName(), info.getFilePath(),
                    info.getFlushReason(), info.getTableProperties().getNumEntries(),
                    info.getSmallestSeqno(), info.getLargestSeqno()));
            flushesCompleted.countDown();
        }

        @Override
        public void onTableFileCreated(TableFileCreationInfo info) {
            createdFiles.add(new CreatedFile(info.getFilePath(), info.getReason(),
                    code(info.getStatus()), info.getFileSize(),
                    info.getTableProperties().getNumEntries()));
        }

        @Override
        public void onTableFileDeleted(TableFileDeletionInfo info) {
            deletedFiles.add(new DeletedFile(info.getFilePath(), code(info.getStatus())));
        }

        @Override
        public void onCompactionBegin(RocksDB db, CompactionJobInfo info) {
            compactionsStarted.add(info.jobId());
        }

        @Override
        public void onCompactionCompleted(RocksDB db, CompactionJobInfo info) {
            try (CompactionJobStats stats = info.stats()) {
                compactions.add(new CompactionRecord(
                        new String(info.columnFamilyName(), StandardCharsets.UTF_8),
                        info.compactionReason(), code(info.status()),
                        List.copyOf(info.inputFiles()), List.copyOf(info.outputFiles()),
                        stats.numInputRecords(), stats.numOutputRecords()));
            }
            compactionsCompleted.countDown();
        }
    }

    record SealedMemtable(String columnFamily, long entries, long deletes) {
    }

    record FlushRecord(String columnFamily, String filePath, FlushReason reason, long entries,
            long smallestSeqno, long largestSeqno) {
    }

    record CreatedFile(String filePath, TableFileCreationReason reason, Status.Code status,
            long size, long entries) {
    }

    record DeletedFile(String filePath, Status.Code status) {
    }

    record CompactionRecord(String columnFamily, CompactionReason reason, Status.Code status,
            List<String> inputFiles, List<String> outputFiles, long inputRecords,
            long outputRecords) {
    }
}
