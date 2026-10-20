/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Bulk CSV import through `LOAD DATA LOCAL INFILE`: loading a client-side file, loading
/// an application-supplied stream through `org.mariadb.jdbc.Statement`, server-reported
/// data warnings and skipped duplicates, and rejection when `allowLocalInfile` is off.
///
/// The scenario is a stock importer that feeds supplier CSV exports into a MariaDB
/// inventory table the way a nightly batch job does, against a real MariaDB server.
public class BulkCsvImportTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-infile-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "inventory";

    private static final String USER = "importer";

    private static final String PASSWORD = "importer-secret";

    private static final String LOAD_OPTIONS = """
            INTO TABLE stock
            FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
            LINES TERMINATED BY '\\n'
            IGNORE 1 LINES
            (sku, name, quantity, unit_price)""";

    private static String url;

    private static MariaDbDataSource importerSource;

    @TempDir
    Path workDirectory;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;

        importerSource = dataSource("allowLocalInfile=true");
        awaitServer(importerSource);

        try (Connection connection = importerSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE stock (
                        sku VARCHAR(32) NOT NULL PRIMARY KEY,
                        name VARCHAR(120) NOT NULL,
                        quantity INT NOT NULL,
                        unit_price DECIMAL(10, 2) NOT NULL
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void emptyStock() throws SQLException {
        try (Connection connection = importerSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM stock");
        }
    }

    @Test
    void importsSupplierFileFromTheClientFilesystem() throws Exception {
        Path export = workDirectory.resolve("supplier-export.csv");
        Files.writeString(export, """
                sku,name,quantity,unit_price
                A-100,Anvil,4,120.00
                B-200,"Bucket, galvanized",30,8.50
                C-300,Chisel,12,14.25
                """, StandardCharsets.UTF_8);

        StockImporter importer = new StockImporter(importerSource);
        int loaded = importer.importFile(export);

        assertThat(loaded).isEqualTo(3);
        assertThat(importer.lastWarnings()).isEmpty();
        assertThat(stockLevels()).containsExactly(
                Map.entry("A-100", new StockRow("Anvil", 4, new BigDecimal("120.00"))),
                Map.entry("B-200", new StockRow("Bucket, galvanized", 30, new BigDecimal("8.50"))),
                Map.entry("C-300", new StockRow("Chisel", 12, new BigDecimal("14.25"))));
    }

    @Test
    void importsAnInMemoryFeedThroughTheDriverStatement() throws Exception {
        String feed = """
                sku,name,quantity,unit_price
                D-400,Drill,7,89.99
                E-500,Easel,2,45.00
                """;

        StockImporter importer = new StockImporter(importerSource);
        int loaded = importer.importStream(new ByteArrayInputStream(feed.getBytes(StandardCharsets.UTF_8)));

        assertThat(loaded).isEqualTo(2);
        assertThat(stockLevels()).containsExactly(
                Map.entry("D-400", new StockRow("Drill", 7, new BigDecimal("89.99"))),
                Map.entry("E-500", new StockRow("Easel", 2, new BigDecimal("45.00"))));
    }

    @Test
    void reportsDataProblemsAsWarningsAndSkipsDuplicates() throws Exception {
        Path export = workDirectory.resolve("messy-export.csv");
        Files.writeString(export, """
                sku,name,quantity,unit_price
                F-600,File,9,6.75
                F-600,File duplicate,1,1.00
                G-700,Gouge,many,11.40
                """, StandardCharsets.UTF_8);

        StockImporter importer = new StockImporter(importerSource);
        int loaded = importer.importFile(export);

        assertThat(loaded).isEqualTo(2);
        List<SQLWarning> warnings = importer.lastWarnings();
        assertThat(warnings).isNotEmpty();
        assertThat(warnings).extracting(SQLWarning::getMessage)
                .anySatisfy(message -> assertThat(message).contains("F-600"))
                .anySatisfy(message -> assertThat(message).contains("quantity"));
        assertThat(stockLevels()).containsExactly(
                Map.entry("F-600", new StockRow("File", 9, new BigDecimal("6.75"))),
                Map.entry("G-700", new StockRow("Gouge", 0, new BigDecimal("11.40"))));
    }

    @Test
    void refusesLocalFilesWhenTheConnectionDoesNotAllowThem() throws Exception {
        Path export = workDirectory.resolve("blocked-export.csv");
        Files.writeString(export, """
                sku,name,quantity,unit_price
                H-800,Hammer,5,19.90
                """, StandardCharsets.UTF_8);

        StockImporter importer = new StockImporter(dataSource("allowLocalInfile=false"));

        assertThatThrownBy(() -> importer.importFile(export))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("allowLocalInfile");
        assertThat(stockLevels()).isEmpty();
    }

    private static Map<String, StockRow> stockLevels() throws SQLException {
        Map<String, StockRow> levels = new LinkedHashMap<>();
        try (Connection connection = importerSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT sku, name, quantity, unit_price FROM stock ORDER BY sku")) {
            while (rows.next()) {
                levels.put(rows.getString("sku"), new StockRow(
                        rows.getString("name"), rows.getInt("quantity"), rows.getBigDecimal("unit_price")));
            }
        }
        return levels;
    }

    private static MariaDbDataSource dataSource(String options) throws SQLException {
        MariaDbDataSource source = new MariaDbDataSource(url + "?" + options);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        return source;
    }

    private static void awaitServer(MariaDbDataSource source) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = source.getConnection()) {
                if (connection.isValid(5)) {
                    return;
                }
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MariaDB did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    /// One inventory line as the importer's reports show it.
    record StockRow(String name, int quantity, BigDecimal unitPrice) {
    }

    /// Nightly batch job that loads supplier CSV exports into the `stock` table.
    static final class StockImporter {

        private final MariaDbDataSource source;

        private final List<SQLWarning> warnings = new ArrayList<>();

        StockImporter(MariaDbDataSource source) {
            this.source = source;
        }

        /// Loads a CSV export the server asks the driver to read from the local disk.
        int importFile(Path export) throws SQLException {
            String path = export.toAbsolutePath().toString().replace("\\", "/");
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                int loaded = statement.executeUpdate("LOAD DATA LOCAL INFILE '" + path + "' " + LOAD_OPTIONS);
                collectWarnings(statement);
                return loaded;
            }
        }

        /// Loads a CSV feed the application already holds, without touching the disk.
        int importStream(InputStream feed) throws SQLException {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.unwrap(org.mariadb.jdbc.Statement.class).setLocalInfileInputStream(feed);
                int loaded = statement.executeUpdate("LOAD DATA LOCAL INFILE 'feed.csv' " + LOAD_OPTIONS);
                collectWarnings(statement);
                return loaded;
            }
        }

        List<SQLWarning> lastWarnings() {
            return List.copyOf(warnings);
        }

        private void collectWarnings(Statement statement) throws SQLException {
            warnings.clear();
            for (SQLWarning warning = statement.getWarnings(); warning != null; warning = warning.getNextWarning()) {
                warnings.add(warning);
            }
        }
    }
}
