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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.api.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * SQL compatibility modes: the `MODE` connection setting and `SET MODE` statement that make the
 * engine accept the dialects of MySQL, Oracle, Microsoft SQL Server and DB2, with their identifier
 * quoting, upsert forms, row limiting, sequences, built-in functions and empty-string semantics.
 *
 * A loyalty-points service that ships against several production databases runs the SQL it
 * already writes for each vendor against H2 in the matching mode, the way its integration
 * suite does, and checks the rows the service would read back.
 */
public class LegacyDialectCompatibilityModesTest {

    @Test
    void mysqlModeAcceptsBackticksAutoIncrementAndUpserts() throws SQLException {
        try (Connection connection = open("MySQL;DATABASE_TO_LOWER=TRUE")) {
            execute(connection, """
                    CREATE TABLE `member` (
                        `id` INT AUTO_INCREMENT PRIMARY KEY,
                        `email` VARCHAR(100) NOT NULL UNIQUE,
                        `tier` VARCHAR(10),
                        `points` INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8
                    """);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO `member` (`email`, `tier`, `points`) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "ana@example.com");
                insert.setString(2, "gold");
                insert.setInt(3, 120);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    assertEquals(1, keys.getInt(1));
                }
            }
            execute(connection, "INSERT INTO `member` (`email`, `points`) VALUES ('ben@example.com', 40)");
            assertEquals(2L, queryLong(connection, "SELECT LAST_INSERT_ID()"));

            int ignored = executeUpdate(connection,
                    "INSERT IGNORE INTO `member` (`email`, `points`) VALUES ('ana@example.com', 999)");
            assertEquals(0, ignored);
            execute(connection, """
                    INSERT INTO `member` (`email`, `points`) VALUES ('ben@example.com', 25)
                    ON DUPLICATE KEY UPDATE `points` = `points` + VALUES(`points`)
                    """);
            execute(connection, "REPLACE INTO `member` (`id`, `email`, `tier`, `points`)"
                    + " VALUES (3, 'cy@example.com', 'silver', 10)");
            execute(connection, "REPLACE INTO `member` (`id`, `email`, `tier`, `points`)"
                    + " VALUES (3, 'cy@example.com', 'gold', 75)");

            assertEquals(List.of("ana@example.com:gold:120", "ben@example.com:none:65", "cy@example.com:gold:75"),
                    queryStrings(connection, """
                            SELECT CONCAT(`email`, ':', IFNULL(`tier`, 'none'), ':', `points`)
                            FROM `member` ORDER BY `id`
                            """));
            assertEquals(List.of("ben@example.com", "cy@example.com"),
                    queryStrings(connection, "SELECT `email` FROM `member` ORDER BY `id` LIMIT 1, 2"));
            assertEquals(List.of("cy@example.com;ana@example.com"), queryStrings(connection, """
                    SELECT GROUP_CONCAT(`email` ORDER BY `points` ASC SEPARATOR ';')
                    FROM `member` WHERE `tier` = 'gold'
                    """));
            assertEquals(List.of("member"), queryStrings(connection, "SHOW TABLES"));
        }
    }

    @Test
    void oracleModeUsesSequencesDualAndTreatsEmptyStringsAsNull() throws SQLException {
        try (Connection connection = open("Oracle")) {
            execute(connection, """
                    CREATE SEQUENCE statement_seq START WITH 100 INCREMENT BY 10;
                    CREATE TABLE points_statement (
                        statement_no NUMBER(10) PRIMARY KEY,
                        member_name VARCHAR2(60) NOT NULL,
                        nickname VARCHAR2(30),
                        balance NUMBER(10, 2) NOT NULL
                    )
                    """);
            String insert = "INSERT INTO points_statement VALUES (statement_seq.NEXTVAL, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                addStatement(statement, "Dora", "", "310.50");
                addStatement(statement, "Emil", "em", "95.00");
                addStatement(statement, "Fay", null, "480.25");
                statement.executeBatch();
            }
            assertEquals(120L, queryLong(connection, "SELECT statement_seq.CURRVAL FROM DUAL"));

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT nickname FROM points_statement WHERE member_name = 'Dora'")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
            }
            assertEquals(List.of("Dora:-:high", "Emil:em:low", "Fay:-:high"), queryStrings(connection, """
                    SELECT member_name || ':' || NVL(nickname, '-') || ':'
                           || DECODE(SIGN(balance - 300), 1, 'high', 'low')
                    FROM points_statement ORDER BY statement_no
                    """));
            assertEquals(List.of("Fay"), queryStrings(connection, """
                    SELECT member_name FROM (
                        SELECT member_name FROM points_statement ORDER BY balance DESC
                    ) WHERE ROWNUM <= 1
                    """));
            assertEquals(List.of("present"), queryStrings(connection,
                    "SELECT NVL2(nickname, 'present', 'absent') FROM points_statement WHERE member_name = 'Emil'"));
            assertEquals(List.of("2024-02-29"),
                    queryStrings(connection, "SELECT TO_CHAR(DATE '2024-02-29', 'YYYY-MM-DD') FROM DUAL"));
        }
    }

    @Test
    void sqlServerModeAcceptsBracketsTopIdentityAndPlusConcatenation() throws SQLException {
        try (Connection connection = open("MSSQLServer")) {
            execute(connection, """
                    CREATE TABLE [redemption] (
                        [id] INT IDENTITY(1, 1) PRIMARY KEY,
                        [member] NVARCHAR(40) NOT NULL,
                        [reward] NVARCHAR(40),
                        [cost] INT NOT NULL
                    )
                    """);
            execute(connection, "INSERT INTO [redemption] ([member], [reward], [cost]) VALUES ('Gus', 'mug', 50)");
            execute(connection, "INSERT INTO [redemption] ([member], [reward], [cost]) VALUES ('Hal', NULL, 20)");
            execute(connection, "INSERT INTO [redemption] ([member], [reward], [cost]) VALUES ('Ivy', 'flight', 900)");
            assertEquals(3L, queryLong(connection, "SELECT SCOPE_IDENTITY()"));

            assertEquals(List.of("Ivy -> flight", "Gus -> mug"), queryStrings(connection, """
                    SELECT TOP 2 [member] + ' -> ' + ISNULL([reward], 'voucher')
                    FROM [redemption] ORDER BY [cost] DESC
                    """));
            assertEquals(List.of("Hal -> voucher"), queryStrings(connection,
                    "SELECT [member] + ' -> ' + ISNULL([reward], 'voucher') FROM [redemption] WHERE LEN([member]) = 3"
                            + " AND [cost] < 30"));
        }
    }

    @Test
    void db2ModeReadsTheDummyTableAndFetchesFirstRows() throws SQLException {
        try (Connection connection = open("DB2")) {
            execute(connection, """
                    CREATE TABLE ledger (entry_id INT PRIMARY KEY, amount DECIMAL(9, 2) NOT NULL);
                    INSERT INTO ledger VALUES (1, 12.50), (2, 7.25), (3, 30.00), (4, 1.75)
                    """);
            assertEquals(List.of("42"), queryStrings(connection, "SELECT 42 FROM SYSIBM.SYSDUMMY1"));
            assertEquals(List.of("3", "1"), queryStrings(connection,
                    "SELECT entry_id FROM ledger ORDER BY amount DESC FETCH FIRST 2 ROWS ONLY"));
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT SUM(amount) FROM ledger")) {
                assertTrue(rows.next());
                assertEquals(new BigDecimal("51.50"), rows.getBigDecimal(1));
            }
        }
    }

    @Test
    void setModeSwitchesTheDialectOfAnOpenSession() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:switch-" + UUID.randomUUID(), "sa", "")) {
            execute(connection, "CREATE TABLE tag (name VARCHAR(20) PRIMARY KEY)");
            execute(connection, "INSERT INTO tag VALUES ('vip')");
            SQLException rejected = assertThrows(SQLException.class,
                    () -> execute(connection, "INSERT IGNORE INTO tag VALUES ('vip')"));
            assertEquals(ErrorCode.SYNTAX_ERROR_2, rejected.getErrorCode());
            assertEquals(List.of("REGULAR"), queryStrings(connection,
                    "SELECT UPPER(SETTING_VALUE) FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'"));

            execute(connection, "SET MODE MySQL");
            assertEquals(0, executeUpdate(connection, "INSERT IGNORE INTO tag VALUES ('vip')"));
            assertEquals(1, executeUpdate(connection, "INSERT IGNORE INTO tag VALUES ('new')"));
            assertEquals(List.of("MYSQL"), queryStrings(connection,
                    "SELECT UPPER(SETTING_VALUE) FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'"));
            assertEquals(List.of("new", "vip"), queryStrings(connection, "SELECT name FROM tag ORDER BY name"));

            execute(connection, "SET MODE Regular");
            SQLException rejectedAgain = assertThrows(SQLException.class,
                    () -> execute(connection, "INSERT IGNORE INTO tag VALUES ('old')"));
            assertEquals(ErrorCode.SYNTAX_ERROR_2, rejectedAgain.getErrorCode());
            assertFalse(queryStrings(connection, "SELECT name FROM tag").contains("old"));
        }
    }

    private static Connection open(String mode) throws SQLException {
        String url = "jdbc:h2:mem:loyalty-" + UUID.randomUUID() + ";MODE=" + mode;
        return DriverManager.getConnection(url, "sa", "");
    }

    private static void addStatement(PreparedStatement statement, String name, String nickname, String balance)
            throws SQLException {
        statement.setString(1, name);
        statement.setString(2, nickname);
        statement.setBigDecimal(3, new BigDecimal(balance));
        statement.addBatch();
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static List<String> queryStrings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
