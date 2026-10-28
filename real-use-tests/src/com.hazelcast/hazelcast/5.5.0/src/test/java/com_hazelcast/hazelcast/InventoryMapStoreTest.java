/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises IMap persistence through a `MapStore` that keeps a warehouse's stock levels in a
 * relational table.
 *
 * An embedded member fronts an H2 `stock` table with two maps. The write-through map loads the
 * whole table eagerly when it is first opened, inserts, updates, and deletes rows inside each
 * map operation, and reloads an evicted entry from the table so a change made directly in the
 * database becomes visible. The write-behind map keeps changes in memory until the application
 * flushes it, and then writes them to the table in one batch.
 */
class InventoryMapStoreTest {

    private static final String WRITE_THROUGH_MAP = "stock-write-through";

    private static final String WRITE_BEHIND_MAP = "stock-write-behind";

    private static final int WRITE_BEHIND_DELAY_SECONDS = 300;

    private final String jdbcUrl = "jdbc:h2:mem:inventory-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    private final StockStore store = new StockStore(jdbcUrl);

    private Connection keepAlive;

    private HazelcastInstance member;

    @BeforeEach
    void startWarehouse() throws SQLException {
        keepAlive = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("CREATE TABLE stock (sku VARCHAR(32) PRIMARY KEY, quantity INT NOT NULL)");
            statement.execute("INSERT INTO stock VALUES ('BOLT-M6', 1200), ('NUT-M6', 950), ('WASHER-M6', 400)");
        }

        Config config = new Config();
        config.setClusterName("inventory-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);

        config.addMapConfig(new MapConfig(WRITE_THROUGH_MAP).setMapStoreConfig(new MapStoreConfig()
                .setEnabled(true)
                .setImplementation(store)
                .setWriteDelaySeconds(0)
                .setInitialLoadMode(MapStoreConfig.InitialLoadMode.EAGER)));
        config.addMapConfig(new MapConfig(WRITE_BEHIND_MAP).setMapStoreConfig(new MapStoreConfig()
                .setEnabled(true)
                .setImplementation(store)
                .setWriteDelaySeconds(WRITE_BEHIND_DELAY_SECONDS)
                .setWriteBatchSize(100)
                .setWriteCoalescing(true)
                .setInitialLoadMode(MapStoreConfig.InitialLoadMode.EAGER)));

        member = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    void stopWarehouse() throws SQLException {
        if (member != null) {
            member.shutdown();
        }
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("SHUTDOWN");
        }
        keepAlive.close();
    }

    @Test
    void writeThroughMapMirrorsEveryChangeInTheTable() throws SQLException {
        IMap<String, Integer> stock = member.getMap(WRITE_THROUGH_MAP);

        assertThat(stock.size()).isEqualTo(3);
        assertThat(stock.get("BOLT-M6")).isEqualTo(1200);
        assertThat(store.loadAllKeysCalls.get()).isEqualTo(1);

        stock.put("SCREW-M4", 3000);
        stock.put("NUT-M6", 900);
        stock.remove("WASHER-M6");

        assertThat(tableContents()).containsExactly(
                Map.entry("BOLT-M6", 1200),
                Map.entry("NUT-M6", 900),
                Map.entry("SCREW-M4", 3000));

        try (Statement statement = keepAlive.createStatement()) {
            statement.executeUpdate("UPDATE stock SET quantity = 1100 WHERE sku = 'BOLT-M6'");
        }
        assertThat(stock.get("BOLT-M6")).isEqualTo(1200);

        int loadsBeforeEviction = store.loadCalls.get();
        assertThat(stock.evict("BOLT-M6")).isTrue();
        assertThat(stock.get("BOLT-M6")).isEqualTo(1100);
        assertThat(store.loadCalls.get()).isEqualTo(loadsBeforeEviction + 1);
        assertThat(tableContents()).containsEntry("BOLT-M6", 1100);

        assertThat(stock.get("ANCHOR-M8")).isNull();
        assertThat(stock.size()).isEqualTo(3);
    }

    @Test
    void writeBehindMapWritesQueuedChangesWhenFlushed() throws SQLException {
        IMap<String, Integer> stock = member.getMap(WRITE_BEHIND_MAP);
        assertThat(stock.size()).isEqualTo(3);

        stock.set("BOLT-M6", 1150);
        stock.set("BOLT-M6", 1125);
        stock.set("RIVET-M5", 600);
        stock.delete("WASHER-M6");

        assertThat(stock.get("BOLT-M6")).isEqualTo(1125);
        assertThat(stock.containsKey("WASHER-M6")).isFalse();
        assertThat(tableContents()).containsExactly(
                Map.entry("BOLT-M6", 1200),
                Map.entry("NUT-M6", 950),
                Map.entry("WASHER-M6", 400));

        stock.flush();

        assertThat(tableContents()).containsExactly(
                Map.entry("BOLT-M6", 1125),
                Map.entry("NUT-M6", 950),
                Map.entry("RIVET-M5", 600));
        assertThat(store.storedQuantities).doesNotContain(1150);
    }

    private Map<String, Integer> tableContents() throws SQLException {
        Map<String, Integer> rows = new LinkedHashMap<>();
        try (Statement statement = keepAlive.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT sku, quantity FROM stock ORDER BY sku")) {
            while (resultSet.next()) {
                rows.put(resultSet.getString("sku"), resultSet.getInt("quantity"));
            }
        }
        return rows;
    }

    /**
     * Persists stock levels keyed by SKU in the `stock` table, one JDBC connection per call.
     */
    static final class StockStore implements MapStore<String, Integer> {

        private final String jdbcUrl;

        final AtomicInteger loadCalls = new AtomicInteger();

        final AtomicInteger loadAllKeysCalls = new AtomicInteger();

        final List<Integer> storedQuantities = new CopyOnWriteArrayList<>();

        StockStore(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        @Override
        public void store(String sku, Integer quantity) {
            storeAll(Map.of(sku, quantity));
        }

        @Override
        public void storeAll(Map<String, Integer> entries) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement merge = connection.prepareStatement(
                         "MERGE INTO stock (sku, quantity) KEY (sku) VALUES (?, ?)")) {
                for (Map.Entry<String, Integer> entry : entries.entrySet()) {
                    merge.setString(1, entry.getKey());
                    merge.setInt(2, entry.getValue());
                    merge.addBatch();
                    storedQuantities.add(entry.getValue());
                }
                merge.executeBatch();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not store stock levels", e);
            }
        }

        @Override
        public void delete(String sku) {
            deleteAll(List.of(sku));
        }

        @Override
        public void deleteAll(Collection<String> skus) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM stock WHERE sku = ?")) {
                for (String sku : skus) {
                    delete.setString(1, sku);
                    delete.addBatch();
                }
                delete.executeBatch();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not delete stock levels", e);
            }
        }

        @Override
        public Integer load(String sku) {
            loadCalls.incrementAndGet();
            return loadAll(List.of(sku)).get(sku);
        }

        @Override
        public Map<String, Integer> loadAll(Collection<String> skus) {
            Map<String, Integer> quantities = new HashMap<>();
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement select = connection.prepareStatement(
                         "SELECT quantity FROM stock WHERE sku = ?")) {
                for (String sku : skus) {
                    select.setString(1, sku);
                    try (ResultSet resultSet = select.executeQuery()) {
                        if (resultSet.next()) {
                            quantities.put(sku, resultSet.getInt(1));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load stock levels", e);
            }
            return quantities;
        }

        @Override
        public Iterable<String> loadAllKeys() {
            loadAllKeysCalls.incrementAndGet();
            List<String> skus = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT sku FROM stock")) {
                while (resultSet.next()) {
                    skus.add(resultSet.getString(1));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not list stock keys", e);
            }
            return skus;
        }
    }
}
