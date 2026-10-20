/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.HStoreConverter;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Stores free-form product attributes in PostgreSQL `hstore` columns through the driver's
 * `java.util.Map` mapping (`setObject` / `getObject` and `HStoreConverter`).
 *
 * A catalog service saves each product variant's attributes as a `Map`, reads them back as a
 * `Map` including `NULL` values and keys that need quoting, filters variants with hstore
 * containment and key-existence operators bound from Java maps, merges and removes attributes in
 * place, and exchanges the textual hstore form with an external system through `HStoreConverter`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductVariantHstoreAttributesTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "catalog";

    private static final String USER = "merchant";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private int port;

    private PGSimpleDataSource dataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION hstore");
            statement.execute("""
                    CREATE TABLE variants (
                        sku        text   PRIMARY KEY,
                        product    text   NOT NULL,
                        attributes hstore NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void attributesRoundTripAsMapsIncludingNullsAndQuotedText() throws Exception {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("color", "midnight blue");
        attributes.put("size", "XL");
        attributes.put("care \"label\"", "wash at 30\\40 C, no => bleach");
        attributes.put("discontinued", null);
        save("TEE-BLUE-XL", "tee", attributes);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT attributes, attributes -> 'color', akeys(attributes) FROM variants WHERE sku = ?")) {
            query.setString(1, "TEE-BLUE-XL");
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.getMetaData().getColumnTypeName(1)).isEqualTo("hstore");
                assertThat(row.getMetaData().getColumnClassName(1)).isEqualTo(Map.class.getName());
                assertThat(row.next()).isTrue();
                Object stored = row.getObject(1);
                assertThat(stored).isInstanceOf(Map.class).isEqualTo(attributes);
                assertThat(row.getString(2)).isEqualTo("midnight blue");
                assertThat((String[]) row.getArray(3).getArray())
                        .containsExactlyInAnyOrder("color", "size", "care \"label\"", "discontinued");
            }
        }
    }

    @Test
    void searchFiltersVariantsWithContainmentAndKeyExistence() throws Exception {
        save("MUG-RED-S", "mug", Map.of("color", "red", "size", "S", "material", "ceramic"));
        save("MUG-RED-L", "mug", Map.of("color", "red", "size", "L", "material", "stoneware", "gift", "yes"));
        save("MUG-WHITE-L", "mug", Map.of("color", "white", "size", "L", "material", "ceramic"));

        assertThat(skusMatching("mug", Map.of("color", "red"))).containsExactly("MUG-RED-L", "MUG-RED-S");
        assertThat(skusMatching("mug", Map.of("size", "L", "material", "ceramic"))).containsExactly("MUG-WHITE-L");
        assertThat(skusMatching("mug", Map.of("color", "green"))).isEmpty();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT sku FROM variants WHERE product = ? AND attributes ?? ? ORDER BY sku")) {
            query.setString(1, "mug");
            query.setString(2, "gift");
            assertThat(skus(query)).containsExactly("MUG-RED-L");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT attributes -> 'material' AS material, count(*)
                        FROM variants WHERE product = ?
                        GROUP BY 1 ORDER BY 1""")) {
            query.setString(1, "mug");
            Map<String, Integer> perMaterial = new LinkedHashMap<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    perMaterial.put(rows.getString("material"), rows.getInt(2));
                }
            }
            assertThat(perMaterial).containsExactly(entry("ceramic", 2), entry("stoneware", 1));
        }
    }

    @Test
    void attributesAreMergedAndRemovedInPlace() throws Exception {
        save("LAMP-BRASS", "lamp", Map.of("finish", "brass", "bulb", "E27", "warranty", "1y"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement merge = connection.prepareStatement(
                    "UPDATE variants SET attributes = attributes || ? WHERE sku = ?")) {
                merge.setObject(1, Map.of("warranty", "2y", "dimmable", "true"));
                merge.setString(2, "LAMP-BRASS");
                assertThat(merge.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement remove = connection.prepareStatement(
                    "UPDATE variants SET attributes = delete(attributes, ?) WHERE sku = ?")) {
                remove.setString(1, "bulb");
                remove.setString(2, "LAMP-BRASS");
                assertThat(remove.executeUpdate()).isEqualTo(1);
            }
        }

        assertThat(load("LAMP-BRASS")).containsOnly(
                entry("finish", "brass"), entry("warranty", "2y"), entry("dimmable", "true"));
    }

    @Test
    void textualFormIsExchangedThroughHStoreConverter() throws Exception {
        Map<String, String> feed = new LinkedHashMap<>();
        feed.put("brand", "Nord & Co");
        feed.put("origin", "\"Bergen\", NO");
        feed.put("recycled", null);
        String exported = HStoreConverter.toString(feed);
        assertThat(exported).contains("\"brand\"=>\"Nord & Co\"", "\"recycled\"=>NULL");
        assertThat(HStoreConverter.fromString(exported)).containsExactlyInAnyOrderEntriesOf(feed);

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO variants (sku, product, attributes) VALUES (?, ?, ?::hstore)")) {
                insert.setString(1, "SCARF-WOOL");
                insert.setString(2, "scarf");
                insert.setString(3, exported);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT attributes::text, attributes -> 'origin' FROM variants WHERE sku = ?")) {
                query.setString(1, "SCARF-WOOL");
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    Map<String, String> imported = HStoreConverter.fromString(row.getString(1));
                    assertThat(imported).containsExactlyInAnyOrderEntriesOf(feed);
                    assertThat(row.getString(2)).isEqualTo("\"Bergen\", NO");
                }
            }
        }
    }

    private void save(String sku, String product, Map<String, String> attributes) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO variants (sku, product, attributes) VALUES (?, ?, ?)")) {
            insert.setString(1, sku);
            insert.setString(2, product);
            insert.setObject(3, attributes);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load(String sku) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT attributes FROM variants WHERE sku = ?")) {
            query.setString(1, sku);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return (Map<String, String>) row.getObject("attributes");
            }
        }
    }

    private List<String> skusMatching(String product, Map<String, String> required) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT sku FROM variants WHERE product = ? AND attributes @> ? ORDER BY sku")) {
            query.setString(1, product);
            query.setObject(2, required);
            return skus(query);
        }
    }

    private static List<String> skus(PreparedStatement query) throws SQLException {
        List<String> skus = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                skus.add(rows.getString("sku"));
            }
        }
        return skus;
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
