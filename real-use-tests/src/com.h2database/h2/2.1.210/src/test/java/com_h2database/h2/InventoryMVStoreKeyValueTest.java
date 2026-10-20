/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.type.LongDataType;
import org.h2.mvstore.type.StringDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MVStore key-value storage engine used directly as an embedded store: persistent file
 * maps, explicit commits and reopen, versioned snapshots and rollback, ordered key navigation,
 * compression, and AES file encryption.
 *
 * A warehouse keeps stock levels per SKU in a typed `MVMap` inside a single store file, commits
 * after each restock, reads back an earlier stock take from a version snapshot, undoes a bad
 * import by rolling back, and protects a supplier price list with an encryption key.
 */
public class InventoryMVStoreKeyValueTest {

    private static final MVMap.Builder<String, Long> STOCK_MAP = new MVMap.Builder<String, Long>()
            .keyType(StringDataType.INSTANCE)
            .valueType(LongDataType.INSTANCE);

    @TempDir
    Path directory;

    @Test
    void persistsCommittedStockAcrossReopen() {
        String file = directory.resolve("warehouse.mv.db").toString();
        try (MVStore store = new MVStore.Builder().fileName(file).compress().autoCommitDisabled().open()) {
            MVMap<String, Long> stock = store.openMap("stock", STOCK_MAP);
            stock.put("KB-01", 10L);
            stock.put("MS-02", 20L);
            stock.put("MN-27", 5L);
            MVMap<String, String> locations = store.openMap("locations");
            locations.put("KB-01", "aisle-3");
            assertTrue(store.hasUnsavedChanges());
            store.commit();
            assertFalse(store.hasUnsavedChanges());
        }

        try (MVStore store = new MVStore.Builder().fileName(file).readOnly().open()) {
            assertTrue(store.isReadOnly());
            assertEquals(Set.of("stock", "locations"), store.getMapNames());
            MVMap<String, Long> stock = store.openMap("stock", STOCK_MAP);
            assertEquals(3L, stock.sizeAsLong());
            assertEquals(20L, stock.get("MS-02"));
            assertEquals("aisle-3", store.<String, String>openMap("locations").get("KB-01"));
        }
    }

    @Test
    void dropsChangesThatWereNeverCommitted() {
        String file = directory.resolve("crash.mv.db").toString();
        MVStore store = new MVStore.Builder().fileName(file).autoCommitDisabled().open();
        MVMap<String, Long> stock = store.openMap("stock", STOCK_MAP);
        stock.put("KB-01", 10L);
        store.commit();
        stock.put("KB-01", 0L);
        stock.put("HS-09", 4L);
        store.closeImmediately();

        try (MVStore reopened = new MVStore.Builder().fileName(file).autoCommitDisabled().open()) {
            MVMap<String, Long> recovered = reopened.openMap("stock", STOCK_MAP);
            assertEquals(Map.of("KB-01", 10L), Map.copyOf(recovered));
        }
    }

    @Test
    void readsEarlierStockTakeFromSnapshotAndRollsBackABadImport() {
        try (MVStore store = new MVStore.Builder().autoCommitDisabled().open()) {
            store.setVersionsToKeep(10);
            MVMap<String, Long> stock = store.openMap("stock", STOCK_MAP);
            stock.put("KB-01", 10L);
            stock.put("MS-02", 20L);
            long stockTake = store.getCurrentVersion();
            store.commit();

            stock.put("KB-01", 7L);
            stock.remove("MS-02");
            // `commit` returns the new version, whose beginning is the post-sales state.
            long afterSales = store.commit();
            assertTrue(afterSales > stockTake);

            MVMap<String, Long> snapshot = stock.openVersion(stockTake);
            assertTrue(snapshot.isReadOnly());
            assertEquals(10L, snapshot.get("KB-01"));
            assertEquals(20L, snapshot.get("MS-02"));
            assertThrows(UnsupportedOperationException.class, () -> snapshot.put("KB-01", 1L));

            stock.put("KB-01", -999L);
            stock.put("XX-00", 1L);
            store.rollbackTo(afterSales);
            assertEquals(Map.of("KB-01", 7L), Map.copyOf(stock));
            assertEquals(afterSales, store.getCurrentVersion());
        }
    }

    @Test
    void navigatesSkusInKeyOrder() {
        try (MVStore store = new MVStore.Builder().autoCommitDisabled().open()) {
            MVMap<String, Long> stock = store.openMap("stock", STOCK_MAP);
            for (String sku : List.of("MS-02", "KB-01", "MN-27", "HS-09", "MS-11", "CB-40")) {
                stock.put(sku, (long) sku.length() + sku.charAt(3));
            }

            assertEquals("CB-40", stock.firstKey());
            assertEquals("MS-11", stock.lastKey());
            assertEquals("MN-27", stock.ceilingKey("MN"));
            assertEquals("KB-01", stock.floorKey("LZ"));
            assertEquals("MS-02", stock.higherKey("MN-27"));
            assertNull(stock.higherKey("MS-11"));
            assertEquals(2L, stock.getKeyIndex("KB-01"));
            assertEquals("HS-09", stock.getKey(1));

            List<String> mouseSkus = new ArrayList<>();
            Cursor<String, Long> cursor = stock.cursor("MS", "MS-~", false);
            while (cursor.hasNext()) {
                mouseSkus.add(cursor.next() + "=" + cursor.getValue());
            }
            assertEquals(List.of("MS-02=53", "MS-11=54"), mouseSkus);

            List<String> descending = new ArrayList<>();
            Iterator<String> reverse = stock.keyIteratorReverse("MN-27");
            while (reverse.hasNext()) {
                descending.add(reverse.next());
            }
            assertEquals(List.of("MN-27", "KB-01", "HS-09", "CB-40"), descending);
        }
    }

    @Test
    void protectsPriceListWithAnEncryptionKey() throws IOException {
        Path path = directory.resolve("prices.mv.db");
        String file = path.toString();
        try (MVStore store = new MVStore.Builder().fileName(file).encryptionKey("s3cret-key".toCharArray())
                .autoCommitDisabled().open()) {
            MVMap<String, String> prices = store.openMap("prices");
            prices.put("supplier", "Acme Peripherals Wholesale");
            prices.put("KB-01", "41.20");
            store.commit();
        }

        String raw = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Acme Peripherals Wholesale"));
        assertFalse(raw.contains("prices"));

        assertThrows(MVStoreException.class, () -> new MVStore.Builder().fileName(file).readOnly().open());
        assertThrows(MVStoreException.class, () -> new MVStore.Builder().fileName(file)
                .encryptionKey("wrong-key!".toCharArray()).readOnly().open());

        try (MVStore store = new MVStore.Builder().fileName(file).encryptionKey("s3cret-key".toCharArray())
                .readOnly().open()) {
            Map<String, String> prices = new LinkedHashMap<>(store.<String, String>openMap("prices"));
            assertEquals(Map.of("supplier", "Acme Peripherals Wholesale", "KB-01", "41.20"), prices);
        }
    }
}
