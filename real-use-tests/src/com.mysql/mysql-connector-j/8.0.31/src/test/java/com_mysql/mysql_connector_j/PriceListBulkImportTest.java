/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.JdbcStatement;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Bulk loading through `LOAD DATA LOCAL INFILE`: a price-list importer that the server
/// refuses while local loading is disabled on the client, loads CSV files confined to an import
/// directory by `allowLoadLocalInfileInPath` (rejecting files outside it), and feeds an in-memory
/// delta through `JdbcStatement.setLocalInfileInputStream`, observing update counts and
/// duplicate-row warnings.
public class PriceListBulkImportTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "pricing";
    private static final String USER = "importer";
    private static final String PASSWORD = "importer-secret";
    private static final String CONTAINER = "real-use-mysql-prices-" + UUID.randomUUID();

    /// The CSV dialect every import uses: quoted fields may contain commas, a header is skipped,
    /// and the price is converted from euros to cents on the way in.
    private static final String CSV_CLAUSES = """
             INTO TABLE price CHARACTER SET utf8mb4
            FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
            LINES TERMINATED BY '\\n'
            IGNORE 1 LINES
            (sku, name, @euros)
            SET price_cents = ROUND(@euros * 100)""";

    @TempDir
    static Path workspace;

    private static int port;

    @BeforeAll
    static void startMysql() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE, "--local-infile=1");
        awaitServer(importer(), TimeUnit.MINUTES.toMillis(3));
        try (Connection connection = importer().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE price (
                        sku VARCHAR(16) PRIMARY KEY,
                        name VARCHAR(64) CHARACTER SET utf8mb4 NOT NULL,
                        price_cents INT NOT NULL
                    ) ENGINE = InnoDB
                    """);
        }
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void importsPriceListsOnlyFromPermittedSources() throws Exception {
        Path imports = Files.createDirectories(workspace.resolve("imports"));
        Path catalog = imports.resolve("catalog.csv");
        Files.writeString(catalog, """
                sku,name,price
                ESP-01,Espresso,2.10
                CAP-01,"Cappuccino, large",3.45
                CRM-01,"Caf\u00e9 cr\u00e8me",3.05
                """, StandardCharsets.UTF_8);
        Path outside = workspace.resolve("outside.csv");
        Files.writeString(outside, "sku,name,price\nHACK-1,Free coffee,0.00\n", StandardCharsets.UTF_8);

        // By default the driver does not offer local file transfer, so the server refuses the load.
        try (Connection connection = importer().getConnection();
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(loadFrom(catalog)))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(3948);
                        assertThat(e.getMessage()).contains("Loading local data is disabled");
                    });
        }
        assertThat(prices()).isEmpty();

        // Confined to the import directory, files inside load and files outside are rejected.
        MysqlDataSource confined = importer();
        confined.setAllowLoadLocalInfileInPath(imports.toString());
        try (Connection connection = confined.getConnection();
             Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(loadFrom(catalog))).isEqualTo(3);
            assertThat(warningCodes(statement.getWarnings())).isEmpty();

            assertThatThrownBy(() -> statement.executeUpdate(loadFrom(outside)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("is not under the safe path");
            Path traversal = imports.resolve("..").resolve("outside.csv");
            assertThatThrownBy(() -> statement.executeUpdate(loadFrom(traversal)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("is not under the safe path");
        }
        Map<String, Integer> afterCatalog = new LinkedHashMap<>();
        afterCatalog.put("CAP-01", 345);
        afterCatalog.put("CRM-01", 305);
        afterCatalog.put("ESP-01", 210);
        assertThat(prices()).containsExactlyEntriesOf(afterCatalog);
        assertThat(names()).containsEntry("CAP-01", "Cappuccino, large")
                .containsEntry("CRM-01", "Caf\u00e9 cr\u00e8me");

        // A delta produced in memory is streamed instead of the named file; existing SKUs are
        // skipped with a duplicate-key warning each, new ones are added.
        MysqlDataSource streaming = importer();
        streaming.setAllowLoadLocalInfile(true);
        try (Connection connection = streaming.getConnection();
             Statement statement = connection.createStatement()) {
            JdbcStatement mysqlStatement = statement.unwrap(JdbcStatement.class);
            mysqlStatement.setLocalInfileInputStream(csv("""
                    sku,name,price
                    ESP-01,Espresso,1.00
                    LAT-01,Latte,3.60
                    MOC-01,"Mocha, iced",4.20
                    CAP-01,"Cappuccino, large",9.99
                    """));
            int loaded = statement.executeUpdate(
                    "LOAD DATA LOCAL INFILE 'delta.csv' IGNORE" + CSV_CLAUSES);
            assertThat(loaded).isEqualTo(2);
            assertThat(warningCodes(statement.getWarnings())).containsExactly(1062, 1062);

            // `REPLACE` overwrites existing rows: each replaced row counts as a delete plus an insert.
            mysqlStatement.setLocalInfileInputStream(csv("""
                    sku,name,price
                    ESP-01,Espresso,2.30
                    """));
            assertThat(statement.executeUpdate(
                    "LOAD DATA LOCAL INFILE 'reprice.csv' REPLACE" + CSV_CLAUSES)).isEqualTo(2);
        }
        Map<String, Integer> afterDelta = new LinkedHashMap<>();
        afterDelta.put("CAP-01", 345);
        afterDelta.put("CRM-01", 305);
        afterDelta.put("ESP-01", 230);
        afterDelta.put("LAT-01", 360);
        afterDelta.put("MOC-01", 420);
        assertThat(prices()).containsExactlyEntriesOf(afterDelta);
    }

    private static MysqlDataSource importer() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE + "?characterEncoding=UTF-8");
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static String loadFrom(Path file) {
        return "LOAD DATA LOCAL INFILE '" + file + "'" + CSV_CLAUSES;
    }

    private static ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Integer> warningCodes(SQLWarning first) {
        List<Integer> codes = new ArrayList<>();
        for (SQLWarning warning = first; warning != null; warning = warning.getNextWarning()) {
            codes.add(warning.getErrorCode());
        }
        return codes;
    }

    private static Map<String, Integer> prices() throws SQLException {
        Map<String, Integer> prices = new LinkedHashMap<>();
        try (Connection connection = importer().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT sku, price_cents FROM price ORDER BY sku")) {
            while (rows.next()) {
                prices.put(rows.getString("sku"), rows.getInt("price_cents"));
            }
        }
        return prices;
    }

    private static Map<String, String> names() throws SQLException {
        Map<String, String> names = new LinkedHashMap<>();
        try (Connection connection = importer().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT sku, name FROM price ORDER BY sku")) {
            while (rows.next()) {
                names.put(rows.getString("sku"), rows.getString("name"));
            }
        }
        return names;
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(MysqlDataSource dataSource, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
