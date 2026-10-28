/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * Column families with atomic `WriteBatch` writes: creating families when a database opens,
 * committing one change across several families in a single batch, reading them back with a
 * multi-family `multiGet`, reopening the database from disk, and dropping a family.
 *
 * An order ledger keeps orders as JSON documents in an `orders` family, a per-customer
 * order-count index in `customer_totals`, and an audit trail in `audit`. Placing an order or
 * cancelling one touches all three families, and the ledger commits each change as one batch,
 * so after a restart the index always agrees with the orders it counts.
 */
public class OrderLedgerColumnFamilyBatchTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dataDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void commitsOrdersAcrossColumnFamiliesAtomicallyAndKeepsThemAfterRestart() throws Exception {
        String path = dataDir.resolve("ledger").toString();

        try (Ledger ledger = Ledger.open(path)) {
            ledger.placeOrder(new Order("o-1001", "alice", 2, 1999));
            ledger.placeOrder(new Order("o-1002", "bob", 1, 4500));
            ledger.placeOrder(new Order("o-1003", "alice", 5, 250));

            assertEquals(2, ledger.orderCount("alice"));
            assertEquals(1, ledger.orderCount("bob"));
            assertEquals(new Order("o-1002", "bob", 1, 4500), ledger.order("o-1002"));
        }

        try (Ledger ledger = Ledger.open(path)) {
            assertEquals(Set.of("default", "orders", "customer_totals", "audit"),
                    Ledger.columnFamilies(path));
            assertEquals(new Order("o-1001", "alice", 2, 1999), ledger.order("o-1001"));
            assertEquals(new Order("o-1003", "alice", 5, 250), ledger.order("o-1003"));
            assertEquals(2, ledger.orderCount("alice"));
            assertEquals("placed o-1003", ledger.auditEntry(3));

            ledger.cancelOrder("o-1001");
        }

        try (Ledger ledger = Ledger.open(path)) {
            assertNull(ledger.order("o-1001"));
            assertEquals(1, ledger.orderCount("alice"));
            assertEquals("cancelled o-1001", ledger.auditEntry(4));
            assertEquals(4, ledger.auditSize());
        }
    }

    @Test
    void keepsColumnFamiliesSeparateAndReadsThemTogetherWithMultiGet() throws Exception {
        String path = dataDir.resolve("multiget").toString();

        try (Ledger ledger = Ledger.open(path)) {
            ledger.placeOrder(new Order("alice", "alice", 3, 1200));

            List<byte[]> values = ledger.db.multiGetAsList(
                    List.of(ledger.orders, ledger.customerTotals, ledger.audit, ledger.orders),
                    List.of(bytes("alice"), bytes("alice"), Ledger.auditKey(1), bytes("missing")));

            assertEquals(new Order("alice", "alice", 3, 1200),
                    JSON.readValue(values.get(0), Order.class));
            assertEquals("1", text(values.get(1)));
            assertEquals("placed alice", text(values.get(2)));
            assertNull(values.get(3));
            assertNull(ledger.db.get(bytes("alice")),
                    "the default family never sees keys written to other families");
        }
    }

    @Test
    void discardsAnUncommittedBatchAndDropsAColumnFamily() throws Exception {
        String path = dataDir.resolve("drop").toString();

        try (Ledger ledger = Ledger.open(path)) {
            ledger.placeOrder(new Order("o-2001", "carol", 1, 999));

            try (WriteBatch abandoned = new WriteBatch()) {
                abandoned.delete(ledger.orders, bytes("o-2001"));
                abandoned.put(ledger.customerTotals, bytes("carol"), bytes("0"));
                assertEquals(2, abandoned.count());
                assertTrue(abandoned.hasPut());
                assertTrue(abandoned.hasDelete());
            }
            assertNotNull(ledger.order("o-2001"), "a batch that is never written changes nothing");
            assertEquals(1, ledger.orderCount("carol"));

            ledger.db.dropColumnFamily(ledger.audit);
        }

        Set<String> remaining = Ledger.columnFamilies(path);
        assertFalse(remaining.contains("audit"));
        assertEquals(Set.of("default", "orders", "customer_totals"), remaining);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** An order as the ledger stores it, serialized to JSON. */
    public record Order(String id, String customer, int quantity, long unitPriceCents) {
    }

    /** The application's storage layer: one database with three column families. */
    private static final class Ledger implements AutoCloseable {

        private static final List<String> FAMILIES = List.of("orders", "customer_totals", "audit");

        private final DBOptions dbOptions;
        private final ColumnFamilyOptions familyOptions;
        private final List<ColumnFamilyHandle> handles;
        private final RocksDB db;
        private final ColumnFamilyHandle orders;
        private final ColumnFamilyHandle customerTotals;
        private final ColumnFamilyHandle audit;

        private Ledger(DBOptions dbOptions, ColumnFamilyOptions familyOptions,
                List<ColumnFamilyHandle> handles, RocksDB db) {
            this.dbOptions = dbOptions;
            this.familyOptions = familyOptions;
            this.handles = handles;
            this.db = db;
            this.orders = handles.get(1);
            this.customerTotals = handles.get(2);
            this.audit = handles.get(3);
        }

        static Ledger open(String path) throws RocksDBException {
            DBOptions dbOptions = new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true);
            ColumnFamilyOptions familyOptions = new ColumnFamilyOptions();
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, familyOptions));
            for (String family : FAMILIES) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(family), familyOptions));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            RocksDB db = RocksDB.open(dbOptions, path, descriptors, handles);
            return new Ledger(dbOptions, familyOptions, handles, db);
        }

        static Set<String> columnFamilies(String path) throws RocksDBException {
            try (Options options = new Options()) {
                return RocksDB.listColumnFamilies(options, path).stream()
                        .map(OrderLedgerColumnFamilyBatchTest::text)
                        .collect(Collectors.toSet());
            }
        }

        static byte[] auditKey(long sequence) {
            return bytes("%08d".formatted(sequence));
        }

        void placeOrder(Order order) throws RocksDBException, IOException {
            byte[] customer = bytes(order.customer());
            try (WriteBatch batch = new WriteBatch(); WriteOptions sync = new WriteOptions().setSync(true)) {
                batch.put(orders, bytes(order.id()), JSON.writeValueAsBytes(order));
                batch.put(customerTotals, customer, bytes(Long.toString(orderCount(order.customer()) + 1)));
                batch.put(audit, auditKey(auditSize() + 1), bytes("placed " + order.id()));
                db.write(sync, batch);
            }
        }

        void cancelOrder(String orderId) throws RocksDBException, IOException {
            Order order = order(orderId);
            assertNotNull(order, "cannot cancel unknown order " + orderId);
            try (WriteBatch batch = new WriteBatch(); WriteOptions sync = new WriteOptions().setSync(true)) {
                batch.delete(orders, bytes(orderId));
                batch.put(customerTotals, bytes(order.customer()),
                        bytes(Long.toString(orderCount(order.customer()) - 1)));
                batch.put(audit, auditKey(auditSize() + 1), bytes("cancelled " + orderId));
                db.write(sync, batch);
            }
        }

        Order order(String orderId) throws RocksDBException, IOException {
            byte[] value = db.get(orders, bytes(orderId));
            return value == null ? null : JSON.readValue(value, Order.class);
        }

        long orderCount(String customer) throws RocksDBException {
            byte[] value = db.get(customerTotals, bytes(customer));
            return value == null ? 0 : Long.parseLong(text(value));
        }

        long auditSize() throws RocksDBException {
            long size = 0;
            while (db.get(audit, auditKey(size + 1)) != null) {
                size++;
            }
            return size;
        }

        String auditEntry(long sequence) throws RocksDBException {
            byte[] value = db.get(audit, auditKey(sequence));
            return value == null ? null : text(value);
        }

        @Override
        public void close() {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
            db.close();
            familyOptions.close();
            dbOptions.close();
        }
    }
}
