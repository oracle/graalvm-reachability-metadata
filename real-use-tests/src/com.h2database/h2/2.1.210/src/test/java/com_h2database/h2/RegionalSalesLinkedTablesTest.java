/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.api.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Linked tables: `CREATE LINKED TABLE` and `CREATE FORCE LINKED TABLE` reaching other H2
 * databases through a JDBC driver class name, reads with pushed-down conditions and joins with
 * local tables, writes and `EMIT UPDATES` through a link, `READONLY` links that reject writes,
 * views over links, the `LINK_SCHEMA` function, and linked tables reported with the `TABLE LINK`
 * storage type in `INFORMATION_SCHEMA`.
 *
 * A head office keeps sales targets in its own in-memory database and federates the sales ledgers
 * of two branch databases on disk: it reports revenue against targets, corrects the north branch
 * ledger through its link, and only reads the archived south branch ledger.
 */
public class RegionalSalesLinkedTablesTest {

    private static final String DRIVER = "org.h2.Driver";

    private static final String BRANCH_USER = "branch";

    private static final String BRANCH_PASSWORD = "ledger-secret";

    private static final String BRANCH_SCHEMA = """
            CREATE USER IF NOT EXISTS branch PASSWORD 'ledger-secret' ADMIN;
            CREATE TABLE sale (
                id INT PRIMARY KEY,
                sku VARCHAR(20) NOT NULL,
                quantity INT NOT NULL,
                amount DECIMAL(10, 2) NOT NULL
            );
            CREATE TABLE returned_sale (
                sale_id INT PRIMARY KEY,
                reason VARCHAR(40) NOT NULL
            );
            """;

    @TempDir
    Path branchDirectory;

    private String northUrl;

    private String southUrl;

    private Connection north;

    private Connection south;

    private Connection headOffice;

    @BeforeEach
    void openDatabases() throws SQLException {
        northUrl = "jdbc:h2:file:" + branchDirectory.resolve("north").toAbsolutePath();
        southUrl = "jdbc:h2:file:" + branchDirectory.resolve("south").toAbsolutePath();
        north = openBranch(northUrl, """
                INSERT INTO sale VALUES
                    (1, 'KB-01', 2, 90.00), (2, 'MS-02', 5, 125.00), (3, 'KB-01', 1, 45.00);
                """);
        south = openBranch(southUrl, """
                INSERT INTO sale VALUES (10, 'MN-27', 1, 310.00), (11, 'MS-02', 4, 100.00);
                INSERT INTO returned_sale VALUES (11, 'damaged');
                """);
        headOffice = DriverManager.getConnection("jdbc:h2:mem:head-office-" + UUID.randomUUID(), "sa", "");
        try (Statement statement = headOffice.createStatement()) {
            statement.execute("""
                    CREATE TABLE region_target (region VARCHAR(10) PRIMARY KEY, target DECIMAL(10, 2) NOT NULL);
                    INSERT INTO region_target VALUES ('NORTH', 250.00), ('SOUTH', 500.00);
                    """);
            statement.execute(linkStatement("CREATE LINKED TABLE", "north_sale", northUrl, "SALE", "EMIT UPDATES"));
            statement.execute(linkStatement("CREATE LINKED TABLE", "south_sale", southUrl, "SALE", "READONLY"));
            statement.execute("""
                    CREATE VIEW all_sales AS
                        SELECT 'NORTH' AS region, id, sku, quantity, amount FROM north_sale
                        UNION ALL
                        SELECT 'SOUTH' AS region, id, sku, quantity, amount FROM south_sale
                    """);
        }
    }

    @AfterEach
    void closeDatabases() throws SQLException {
        headOffice.close();
        for (Connection branch : List.of(north, south)) {
            try (Statement statement = branch.createStatement()) {
                statement.execute("SHUTDOWN");
            }
            branch.close();
        }
    }

    @Test
    void reportsRevenueAgainstTargetsAcrossLinkedBranches() throws SQLException {
        String report = """
                SELECT t.region, SUM(s.amount) AS revenue, SUM(s.amount) >= t.target AS met
                FROM all_sales s JOIN region_target t ON t.region = s.region
                GROUP BY t.region, t.target
                ORDER BY t.region
                """;
        Map<String, String> revenue = new LinkedHashMap<>();
        try (Statement statement = headOffice.createStatement(); ResultSet rows = statement.executeQuery(report)) {
            while (rows.next()) {
                revenue.put(rows.getString("region"), rows.getBigDecimal("revenue") + "/" + rows.getBoolean("met"));
            }
        }
        assertEquals(Map.of("NORTH", "260.00/true", "SOUTH", "410.00/false"), revenue);

        try (PreparedStatement bySku = headOffice.prepareStatement(
                "SELECT region, id FROM all_sales WHERE sku = ? AND quantity >= ? ORDER BY region, id")) {
            bySku.setString(1, "MS-02");
            bySku.setInt(2, 4);
            assertEquals(List.of("NORTH:2", "SOUTH:11"), readPairs(bySku.executeQuery()));
            bySku.setString(1, "KB-01");
            bySku.setInt(2, 2);
            assertEquals(List.of("NORTH:1"), readPairs(bySku.executeQuery()));
        }
    }

    @Test
    void writesThroughTheNorthLinkLandInTheBranchDatabase() throws SQLException {
        try (Statement statement = headOffice.createStatement()) {
            assertEquals(1, statement.executeUpdate("INSERT INTO north_sale VALUES (4, 'MN-27', 1, 300.00)"));
            assertEquals(2, statement.executeUpdate("UPDATE north_sale SET amount = amount * 0.9 WHERE sku = 'KB-01'"));
            assertEquals(1, statement.executeUpdate("DELETE FROM north_sale WHERE id = 2"));
        }

        Map<Integer, BigDecimal> branchLedger = new LinkedHashMap<>();
        try (Statement statement = north.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, amount FROM sale ORDER BY id")) {
            while (rows.next()) {
                branchLedger.put(rows.getInt("id"), rows.getBigDecimal("amount"));
            }
        }
        assertEquals(Map.of(1, new BigDecimal("81.00"), 3, new BigDecimal("40.50"), 4, new BigDecimal("300.00")),
                branchLedger);

        try (Statement statement = north.createStatement()) {
            statement.executeUpdate("INSERT INTO sale VALUES (5, 'MS-02', 2, 50.00)");
        }
        assertEquals(new BigDecimal("471.50"), querySingleDecimal("SELECT SUM(amount) FROM north_sale"));
    }

    @Test
    void readOnlyLinkRejectsWritesAndLeavesTheSouthLedgerUntouched() throws SQLException {
        try (Statement statement = headOffice.createStatement()) {
            SQLException insert = assertThrows(SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO south_sale VALUES (12, 'KB-01', 1, 45.00)"));
            assertEquals(ErrorCode.DATABASE_IS_READ_ONLY, insert.getErrorCode());
            SQLException delete = assertThrows(SQLException.class,
                    () -> statement.executeUpdate("DELETE FROM south_sale"));
            assertEquals(ErrorCode.DATABASE_IS_READ_ONLY, delete.getErrorCode());
        }
        try (Statement statement = south.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sale")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void linkSchemaMirrorsEveryBranchTableIntoALocalSchema() throws SQLException {
        List<String> linkedTables = new ArrayList<>();
        try (Statement statement = headOffice.createStatement()) {
            statement.execute("CREATE SCHEMA south_archive");
            try (PreparedStatement link = headOffice.prepareStatement(
                    "SELECT * FROM LINK_SCHEMA('SOUTH_ARCHIVE', ?, ?, ?, ?, 'PUBLIC')")) {
                link.setString(1, DRIVER);
                link.setString(2, southUrl);
                link.setString(3, BRANCH_USER);
                link.setString(4, BRANCH_PASSWORD);
                try (ResultSet rows = link.executeQuery()) {
                    while (rows.next()) {
                        linkedTables.add(rows.getString(1));
                    }
                }
            }
            linkedTables.sort(null);
            assertEquals(List.of("RETURNED_SALE", "SALE"), linkedTables);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT s.id, r.reason FROM south_archive.sale s
                    LEFT JOIN south_archive.returned_sale r ON r.sale_id = s.id
                    ORDER BY s.id
                    """)) {
                List<String> outcomes = new ArrayList<>();
                while (rows.next()) {
                    outcomes.add(rows.getInt(1) + ":" + rows.getString(2));
                }
                assertEquals(List.of("10:null", "11:damaged"), outcomes);
            }

            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_schema, table_name, is_insertable_into FROM information_schema.tables
                    WHERE storage_type = 'TABLE LINK' ORDER BY table_schema, table_name
                    """)) {
                List<String> links = new ArrayList<>();
                while (rows.next()) {
                    links.add(rows.getString(1) + "." + rows.getString(2) + ":" + rows.getString(3));
                }
                assertEquals(List.of("PUBLIC.NORTH_SALE:YES", "PUBLIC.SOUTH_SALE:NO",
                        "SOUTH_ARCHIVE.RETURNED_SALE:YES", "SOUTH_ARCHIVE.SALE:YES"), links);
            }
        }
    }

    @Test
    void forcedLinkToAMissingBranchFailsUntilTheBranchExists() throws SQLException {
        String eastUrl = "jdbc:h2:file:" + branchDirectory.resolve("east").toAbsolutePath();
        String existingEastUrl = eastUrl + ";IFEXISTS=TRUE";
        try (Statement statement = headOffice.createStatement()) {
            statement.execute(linkStatement("CREATE FORCE LINKED TABLE", "east_sale", existingEastUrl, "SALE", ""));
            assertThrows(SQLException.class, () -> statement.executeQuery("SELECT COUNT(*) FROM east_sale"));

            Connection east = openBranch(eastUrl, "INSERT INTO sale VALUES (20, 'KB-01', 3, 135.00);");
            statement.execute("DROP TABLE east_sale");
            statement.execute(linkStatement("CREATE LINKED TABLE", "east_sale", existingEastUrl, "SALE", ""));
            assertEquals(new BigDecimal("135.00"), querySingleDecimal("SELECT SUM(amount) FROM east_sale"));
            statement.execute("DROP TABLE east_sale");
            try (east; Statement shutdown = east.createStatement()) {
                shutdown.execute("SHUTDOWN");
            }
        }
    }

    private static Connection openBranch(String url, String seed) throws SQLException {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute(BRANCH_SCHEMA);
            statement.execute(seed);
        }
        return connection;
    }

    private static String linkStatement(String create, String name, String url, String table, String options) {
        return "%s %s('%s', '%s', '%s', '%s', 'PUBLIC', '%s') %s"
                .formatted(create, name, DRIVER, url, BRANCH_USER, BRANCH_PASSWORD, table, options);
    }

    private BigDecimal querySingleDecimal(String sql) throws SQLException {
        try (Statement statement = headOffice.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getBigDecimal(1);
        }
    }

    private static List<String> readPairs(ResultSet rows) throws SQLException {
        try (rows) {
            List<String> pairs = new ArrayList<>();
            while (rows.next()) {
                pairs.add(rows.getString(1) + ":" + rows.getInt(2));
            }
            return pairs;
        }
    }
}
