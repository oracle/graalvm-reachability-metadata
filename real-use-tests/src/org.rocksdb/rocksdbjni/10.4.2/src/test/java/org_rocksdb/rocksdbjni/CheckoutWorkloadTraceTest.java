/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.AbstractTraceWriter;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.TraceOptions;
import org.rocksdb.WriteBatch;

/**
 * Query tracing with a Java trace sink: `RocksDB.startTrace` and `endTrace` driving an
 * `AbstractTraceWriter` through its `write`, `getFileSize` and `TraceOptions` size-cap callbacks.
 *
 * A checkout service keeps carts in RocksDB. Before a capacity-planning exercise the operators
 * switch on query tracing for a window of live traffic and stream every trace record into an
 * in-process capture instead of a local file. An offline analyzer then decodes the records into
 * the operations the service issued, so the workload can be replayed against a candidate setup.
 */
public class CheckoutWorkloadTraceTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void capturesEveryOperationOfATracingWindowInOrder() throws Exception {
        try (CheckoutStore store = CheckoutStore.open(workDir.resolve("carts").toString());
                WorkloadCapture capture = new WorkloadCapture()) {
            store.saveCart("cart-0001", "book x1");

            store.db.startTrace(new TraceOptions(), capture);
            store.saveCart("cart-0002", "lamp x2");
            assertEquals("book x1", store.loadCart("cart-0001"));
            assertEquals(Arrays.asList("book x1", "lamp x2", null),
                    store.loadCarts("cart-0001", "cart-0002", "cart-0404"));
            assertEquals(List.of("cart-0001", "cart-0002"), store.cartsFrom("cart-"));
            assertEquals("cart-0002", store.lastCartBefore("cart-9999"));
            store.abandonCart("cart-0001");
            store.db.endTrace();

            store.saveCart("cart-0003", "after the window");

            List<TraceRecord> records = capture.records();
            assertEquals(List.of(OpType.BEGIN, OpType.WRITE, OpType.GET, OpType.MULTI_GET,
                    OpType.ITERATOR_SEEK, OpType.ITERATOR_SEEK_FOR_PREV, OpType.WRITE, OpType.END),
                    records.stream().map(TraceRecord::type).toList(),
                    "only the window is traced, one record per call, bracketed by begin and end");

            String header = records.get(0).payloadText();
            assertTrue(header.startsWith("feedcafedeadbeef"), header);
            assertTrue(header.contains("RocksDB Version: 10.4"), header);

            for (int i = 1; i < records.size(); i++) {
                assertTrue(records.get(i).timestampMicros() >= records.get(i - 1).timestampMicros(),
                        "records arrive in the order the service issued them");
            }

            try (WriteBatch saved = records.get(1).writeBatch();
                    WriteBatch abandoned = records.get(6).writeBatch()) {
                assertEquals(1, saved.count());
                assertTrue(contains(saved.data(), bytes("cart-0002")));
                assertTrue(contains(saved.data(), bytes("lamp x2")));
                assertEquals(1, abandoned.count());
                assertTrue(contains(abandoned.data(), bytes("cart-0001")));
            }
            assertEquals("cart-0001", records.get(2).lookupKey());
            assertEquals(List.of("cart-0001", "cart-0002", "cart-0404"),
                    records.get(3).multiGetKeys());
            assertEquals("cart-", records.get(4).lookupKey());
            assertEquals("cart-9999", records.get(5).lookupKey());

            assertEquals(records.stream().mapToLong(record -> record.raw().length).sum(),
                    capture.getFileSize(), "the sink reports every byte it was handed");
        }
    }

    @Test
    void sizeCapStopsTracingWhileTheServiceKeepsServing() throws Exception {
        long cap = 1024;
        try (CheckoutStore store = CheckoutStore.open(workDir.resolve("carts").toString());
                WorkloadCapture capture = new WorkloadCapture()) {
            store.db.startTrace(new TraceOptions(cap), capture);
            for (int i = 0; i < 200; i++) {
                store.saveCart(String.format("cart-%04d", i), "item-" + i);
            }
            int capturedAtCap = capture.records().size();
            assertEquals("item-150", store.loadCart("cart-0150"));
            store.db.endTrace();

            List<TraceRecord> records = capture.records();
            assertEquals(OpType.BEGIN, records.get(0).type());
            assertTrue(capturedAtCap < 200, "tracing stopped well before the burst ended");
            assertTrue(capture.getFileSize() >= cap, "the cap was reached");
            long largestRecord = records.stream()
                    .mapToLong(record -> record.raw().length)
                    .max()
                    .orElseThrow();
            assertTrue(capture.getFileSize() <= cap + largestRecord,
                    "nothing is traced once the sink reports the cap");
            assertTrue(records.stream().noneMatch(r -> r.type() == OpType.GET),
                    "the read after the cap was not traced");

            assertEquals(200, store.cartsFrom("cart-").size(), "every write still landed");
        }
    }

    @Test
    void consecutiveWindowsGoToSeparateSinks() throws Exception {
        try (CheckoutStore store = CheckoutStore.open(workDir.resolve("carts").toString());
                WorkloadCapture morning = new WorkloadCapture();
                WorkloadCapture evening = new WorkloadCapture()) {
            store.db.startTrace(new TraceOptions(), morning);
            store.saveCart("cart-0100", "morning order");
            store.db.endTrace();

            store.db.startTrace(new TraceOptions(), evening);
            assertEquals("morning order", store.loadCart("cart-0100"));
            store.db.endTrace();

            assertEquals(List.of(OpType.BEGIN, OpType.WRITE, OpType.END),
                    morning.records().stream().map(TraceRecord::type).toList());
            assertEquals(List.of(OpType.BEGIN, OpType.GET, OpType.END),
                    evening.records().stream().map(TraceRecord::type).toList());
            assertEquals("cart-0100", evening.records().get(1).lookupKey());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            if (Arrays.equals(haystack, start, start + needle.length, needle, 0, needle.length)) {
                return true;
            }
        }
        return false;
    }

    /** The cart store the checkout service talks to. */
    private static final class CheckoutStore implements AutoCloseable {

        private final Options options;
        private final RocksDB db;

        private CheckoutStore(Options options, RocksDB db) {
            this.options = options;
            this.db = db;
        }

        static CheckoutStore open(String path) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true);
            return new CheckoutStore(options, RocksDB.open(options, path));
        }

        void saveCart(String cartId, String contents) throws RocksDBException {
            db.put(bytes(cartId), bytes(contents));
        }

        void abandonCart(String cartId) throws RocksDBException {
            db.delete(bytes(cartId));
        }

        String loadCart(String cartId) throws RocksDBException {
            byte[] value = db.get(bytes(cartId));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        }

        List<String> loadCarts(String... cartIds) throws RocksDBException {
            List<byte[]> keys = new ArrayList<>();
            for (String cartId : cartIds) {
                keys.add(bytes(cartId));
            }
            List<String> carts = new ArrayList<>();
            for (byte[] value : db.multiGetAsList(keys)) {
                carts.add(value == null ? null : new String(value, StandardCharsets.UTF_8));
            }
            return carts;
        }

        List<String> cartsFrom(String prefix) {
            List<String> cartIds = new ArrayList<>();
            try (ReadOptions readOptions = new ReadOptions();
                    RocksIterator it = db.newIterator(readOptions)) {
                for (it.seek(bytes(prefix)); it.isValid(); it.next()) {
                    cartIds.add(new String(it.key(), StandardCharsets.UTF_8));
                }
            }
            return cartIds;
        }

        String lastCartBefore(String bound) {
            try (ReadOptions readOptions = new ReadOptions();
                    RocksIterator it = db.newIterator(readOptions)) {
                it.seekForPrev(bytes(bound));
                return it.isValid() ? new String(it.key(), StandardCharsets.UTF_8) : null;
            }
        }

        @Override
        public void close() {
            db.close();
            options.close();
        }
    }

    /** Trace record kinds the analyzer understands, keyed by their wire tag. */
    private enum OpType {
        BEGIN(1), END(2), WRITE(3), GET(4), ITERATOR_SEEK(5), ITERATOR_SEEK_FOR_PREV(6),
        MULTI_GET(13);

        private final int tag;

        OpType(int tag) {
            this.tag = tag;
        }

        static OpType of(int tag) {
            for (OpType type : values()) {
                if (type.tag == tag) {
                    return type;
                }
            }
            throw new IllegalArgumentException("unexpected trace record type " + tag);
        }
    }

    /**
     * One decoded trace record: a little-endian timestamp, a type byte and a length-prefixed
     * payload whose operation payloads start with a 64-bit field map.
     */
    private record TraceRecord(byte[] raw, long timestampMicros, OpType type, byte[] payload) {

        private static final int PAYLOAD_MAP_BYTES = Long.BYTES;
        private static final int COLUMN_FAMILY_ID_BYTES = Integer.BYTES;

        static TraceRecord decode(byte[] raw) {
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            long timestamp = buffer.getLong();
            OpType type = OpType.of(buffer.get());
            byte[] payload = new byte[buffer.getInt()];
            buffer.get(payload);
            assertEquals(0, buffer.remaining(), "one record per write callback");
            return new TraceRecord(raw, timestamp, type, payload);
        }

        String payloadText() {
            return new String(payload, StandardCharsets.ISO_8859_1);
        }

        /** The write batch a write record carries, ready to be replayed. */
        WriteBatch writeBatch() {
            assertEquals(OpType.WRITE, type);
            ByteBuffer buffer = operationFields();
            return new WriteBatch(readLengthPrefixed(buffer));
        }

        /** The key of a point lookup or an iterator seek on the default column family. */
        String lookupKey() {
            assertTrue(type == OpType.GET || type == OpType.ITERATOR_SEEK
                    || type == OpType.ITERATOR_SEEK_FOR_PREV, type.name());
            ByteBuffer buffer = operationFields();
            assertEquals(0, buffer.getInt(), "default column family");
            return new String(readLengthPrefixed(buffer), StandardCharsets.UTF_8);
        }

        List<String> multiGetKeys() {
            assertEquals(OpType.MULTI_GET, type);
            ByteBuffer buffer = operationFields();
            int size = buffer.getInt();
            ByteBuffer columnFamilies = ByteBuffer.wrap(readLengthPrefixed(buffer))
                    .order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer keys = ByteBuffer.wrap(readLengthPrefixed(buffer));
            List<String> decoded = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                assertEquals(0, columnFamilies.getInt(), "default column family");
                decoded.add(new String(readLengthPrefixed(keys), StandardCharsets.UTF_8));
            }
            assertEquals(0, keys.remaining());
            return decoded;
        }

        private ByteBuffer operationFields() {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(PAYLOAD_MAP_BYTES);
            return buffer;
        }

        private static byte[] readLengthPrefixed(ByteBuffer buffer) {
            int length = 0;
            for (int shift = 0;; shift += 7) {
                byte next = buffer.get();
                length |= (next & 0x7f) << shift;
                if ((next & 0x80) == 0) {
                    break;
                }
            }
            byte[] value = new byte[length];
            buffer.get(value);
            return value;
        }
    }

    /** The in-process sink the trace streams into; RocksDB polls its size to enforce the cap. */
    private static final class WorkloadCapture extends AbstractTraceWriter {

        private final List<byte[]> chunks = new ArrayList<>();
        private long bytesWritten;

        @Override
        public void write(Slice data) {
            byte[] chunk = data.data();
            chunks.add(chunk);
            bytesWritten += chunk.length;
        }

        @Override
        public void closeWriter() {
        }

        @Override
        public long getFileSize() {
            return bytesWritten;
        }

        List<TraceRecord> records() {
            return chunks.stream().map(TraceRecord::decode).toList();
        }
    }
}
