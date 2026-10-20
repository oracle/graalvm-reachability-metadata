/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.util.PGobject;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stores product attributes in PostgreSQL json and jsonb columns bound and read through
 * `PGobject` custom-type values.
 *
 * A catalog service behind a HikariCP pool serializes attribute documents with Jackson into
 * `PGobject` parameters typed `jsonb`, filters products with jsonb containment and key
 * extraction, patches a document with `jsonb_set`, reads columns back as `PGobject` with the
 * server type name, and observes that a `json` column keeps the submitted text verbatim while
 * `jsonb` normalizes it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductCatalogJsonbTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "catalog";

    private static final String USER = "catalog";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();

    private HikariDataSource pool;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
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

        String jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/" + DATABASE + "?connectTimeout=5";
        awaitDatabase(jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setPoolName("catalog-pool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(2);
        pool = new HikariDataSource(config);

        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE products (
                        sku        text  PRIMARY KEY,
                        name       text  NOT NULL,
                        attributes jsonb NOT NULL,
                        raw_import json  NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        if (pool != null) {
            pool.close();
        }
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void catalogFiltersAndPatchesJsonbAttributes() throws Exception {
        ObjectNode lamp = mapper.createObjectNode();
        lamp.put("color", "red");
        lamp.put("watts", 40);
        lamp.putArray("tags").add("lighting").add("indoor");
        ObjectNode kettle = mapper.createObjectNode();
        kettle.put("color", "steel");
        kettle.put("watts", 2200);
        kettle.putArray("tags").add("kitchen");
        ObjectNode lantern = mapper.createObjectNode();
        lantern.put("color", "red");
        lantern.put("watts", 5);
        lantern.putArray("tags").add("lighting").add("outdoor");

        try (Connection connection = pool.getConnection()) {
            insert(connection, "LMP-1", "Desk lamp", lamp);
            insert(connection, "KTL-1", "Kettle", kettle);
            insert(connection, "LTN-1", "Camping lantern", lantern);

            ObjectNode outdoorLighting = mapper.createObjectNode();
            outdoorLighting.putArray("tags").add("lighting").add("outdoor");
            assertThat(skusContaining(connection, outdoorLighting)).containsExactly("LTN-1");

            ObjectNode red = mapper.createObjectNode();
            red.put("color", "red");
            assertThat(skusContaining(connection, red)).containsExactly("LMP-1", "LTN-1");

            try (PreparedStatement byWatts = connection.prepareStatement("""
                    SELECT sku, attributes->>'color' FROM products
                    WHERE (attributes->>'watts')::int > ? ORDER BY sku""")) {
                byWatts.setInt(1, 100);
                try (ResultSet rows = byWatts.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("KTL-1");
                    assertThat(rows.getString(2)).isEqualTo("steel");
                    assertThat(rows.next()).isFalse();
                }
            }

            try (PreparedStatement patch = connection.prepareStatement(
                    "UPDATE products SET attributes = jsonb_set(attributes, '{color}', ?) WHERE sku = ?")) {
                patch.setObject(1, jsonb(mapper.getNodeFactory().textNode("blue")));
                patch.setString(2, "LMP-1");
                assertThat(patch.executeUpdate()).isEqualTo(1);
            }

            PGobject stored = attributesOf(connection, "LMP-1");
            assertThat(stored.getType()).isEqualTo("jsonb");
            JsonNode patched = mapper.readTree(stored.getValue());
            assertThat(patched.get("color").asText()).isEqualTo("blue");
            assertThat(patched.get("watts").asInt()).isEqualTo(40);
            assertThat(patched.get("tags")).extracting(JsonNode::asText).containsExactly("lighting", "indoor");
            assertThat(skusContaining(connection, red)).containsExactly("LTN-1");
        }
    }

    @Test
    void jsonColumnKeepsImportTextWhileJsonbNormalizesIt() throws Exception {
        String importedText = "{\"b\": 1,  \"a\": [true, null], \"b\": 2}";
        try (Connection connection = pool.getConnection()) {
            PGobject raw = new PGobject();
            raw.setType("json");
            raw.setValue(importedText);
            PGobject normalized = new PGobject();
            normalized.setType("jsonb");
            normalized.setValue(importedText);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO products (sku, name, attributes, raw_import) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, "IMP-1");
                insert.setString(2, "Imported item");
                insert.setObject(3, normalized);
                insert.setObject(4, raw);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT attributes, raw_import FROM products WHERE sku = ?")) {
                select.setString(1, "IMP-1");
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    PGobject attributes = (PGobject) row.getObject("attributes");
                    PGobject rawImport = (PGobject) row.getObject("raw_import");

                    assertThat(rawImport.getType()).isEqualTo("json");
                    assertThat(rawImport.getValue()).isEqualTo(importedText);
                    assertThat(attributes.getType()).isEqualTo("jsonb");
                    assertThat(attributes.getValue()).isEqualTo("{\"a\": [true, null], \"b\": 2}");

                    JsonNode document = mapper.readTree(attributes.getValue());
                    assertThat(document.get("b").asInt()).isEqualTo(2);
                    assertThat(document.get("a").get(1).isNull()).isTrue();
                }
            }
        }
    }

    private void insert(Connection connection, String sku, String name, JsonNode attributes)
            throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO products (sku, name, attributes, raw_import) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, sku);
            insert.setString(2, name);
            insert.setObject(3, jsonb(attributes));
            PGobject raw = new PGobject();
            raw.setType("json");
            raw.setValue(mapper.writeValueAsString(attributes));
            insert.setObject(4, raw);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    private List<String> skusContaining(Connection connection, JsonNode fragment) throws Exception {
        List<String> skus = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT sku FROM products WHERE attributes @> ? ORDER BY sku")) {
            query.setObject(1, jsonb(fragment));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    skus.add(rows.getString(1));
                }
            }
        }
        return skus;
    }

    private static PGobject attributesOf(Connection connection, String sku) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT attributes FROM products WHERE sku = ?")) {
            select.setString(1, sku);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getObject(1, PGobject.class);
            }
        }
    }

    private PGobject jsonb(JsonNode document) throws Exception {
        return normalized(mapper.writeValueAsString(document));
    }

    private static PGobject normalized(String text) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(text);
        return value;
    }

    private void awaitDatabase(String jdbcUrl) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
                    Statement statement = probe.createStatement()) {
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
