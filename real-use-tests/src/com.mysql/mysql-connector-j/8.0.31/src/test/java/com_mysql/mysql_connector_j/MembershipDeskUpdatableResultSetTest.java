/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.mysql.cj.jdbc.exceptions.NotUpdatable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Scrollable and updatable result sets: a membership desk scrolls a member roster backwards and
/// by absolute and relative positions, edits rows in place through a `CONCUR_UPDATABLE` cursor
/// (`updateRow`, `cancelRowUpdates`, `moveToInsertRow`/`insertRow`, `deleteRow`), picks up a
/// concurrent change with `refreshRow`, and sees an aggregate query downgraded to read-only.
public class MembershipDeskUpdatableResultSetTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "library";
    private static final String USER = "desk";
    private static final String PASSWORD = "desk-secret";
    private static final String CONTAINER = "real-use-mysql-members-" + UUID.randomUUID();
    private static final String ROSTER = "SELECT id, name, tier, credits, joined FROM members ORDER BY id";

    private static MysqlDataSource dataSource;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer(TimeUnit.MINUTES.toMillis(3));
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @BeforeEach
    void seedRoster() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS members");
            statement.execute("""
                    CREATE TABLE members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        tier VARCHAR(16) NOT NULL,
                        credits INT NOT NULL,
                        joined DATE NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    INSERT INTO members (name, tier, credits, joined) VALUES
                        ('Ada', 'gold', 40, '2019-03-01'),
                        ('Brian', 'basic', 5, '2020-07-15'),
                        ('Chiara', 'gold', 12, '2021-01-09'),
                        ('Dmitri', 'basic', 0, '2022-11-30'),
                        ('Elif', 'silver', 25, '2023-05-21')
                    """);
        }
    }

    @Test
    void scrollsTheRosterByPosition() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement(
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             ResultSet roster = statement.executeQuery(ROSTER)) {
            assertThat(roster.getType()).isEqualTo(ResultSet.TYPE_SCROLL_INSENSITIVE);
            assertThat(roster.isBeforeFirst()).isTrue();

            assertThat(roster.last()).isTrue();
            assertThat(roster.getRow()).isEqualTo(5);
            assertThat(roster.getString("name")).isEqualTo("Elif");

            assertThat(roster.absolute(2)).isTrue();
            assertThat(roster.getString("name")).isEqualTo("Brian");
            assertThat(roster.relative(2)).isTrue();
            assertThat(roster.getString("name")).isEqualTo("Dmitri");
            assertThat(roster.previous()).isTrue();
            assertThat(roster.getString("name")).isEqualTo("Chiara");
            assertThat(roster.absolute(-2)).isTrue();
            assertThat(roster.getString("name")).isEqualTo("Dmitri");
            assertThat(roster.getDate("joined").toLocalDate()).isEqualTo(LocalDate.of(2022, 11, 30));

            assertThat(roster.first()).isTrue();
            assertThat(roster.isFirst()).isTrue();
            assertThat(roster.absolute(9)).isFalse();
            assertThat(roster.isAfterLast()).isTrue();

            List<String> newestFirst = new ArrayList<>();
            while (roster.previous()) {
                newestFirst.add(roster.getString("name"));
            }
            assertThat(newestFirst).containsExactly("Elif", "Dmitri", "Chiara", "Brian", "Ada");
            assertThat(roster.isBeforeFirst()).isTrue();
        }
    }

    @Test
    void editsMembersInPlaceThroughAnUpdatableCursor() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     ROSTER, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             ResultSet roster = query.executeQuery()) {
            assertThat(roster.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);

            // Gold members get a bonus, written back row by row.
            while (roster.next()) {
                if ("gold".equals(roster.getString("tier"))) {
                    roster.updateInt("credits", roster.getInt("credits") + 100);
                    roster.updateRow();
                }
            }

            // A pending change is discarded before it reaches the server.
            assertThat(roster.absolute(2)).isTrue();
            roster.updateString("tier", "platinum");
            roster.cancelRowUpdates();
            assertThat(roster.getString("tier")).isEqualTo("basic");

            // Brian upgrades; the cursor shows the new value immediately.
            roster.updateString("tier", "silver");
            roster.updateInt("credits", 30);
            roster.updateRow();
            assertThat(roster.getString("tier")).isEqualTo("silver");

            // A new member signs up through the insert row.
            roster.moveToInsertRow();
            roster.updateString("name", "Farid");
            roster.updateString("tier", "basic");
            roster.updateInt("credits", 10);
            roster.updateDate("joined", Date.valueOf(LocalDate.of(2024, 2, 29)));
            roster.insertRow();
            roster.moveToCurrentRow();

            // Dmitri cancels the membership.
            assertThat(roster.absolute(4)).isTrue();
            assertThat(roster.getString("name")).isEqualTo("Dmitri");
            roster.deleteRow();
        }

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Ada", "gold:140");
        expected.put("Brian", "silver:30");
        expected.put("Chiara", "gold:112");
        expected.put("Elif", "silver:25");
        expected.put("Farid", "basic:10");
        assertThat(rosterSnapshot()).containsExactlyEntriesOf(expected);
        assertThat(joinedOn("Farid")).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void refreshRowPicksUpAConcurrentChange() throws SQLException {
        try (Connection desk = dataSource.getConnection();
             Statement statement = desk.createStatement(
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             ResultSet roster = statement.executeQuery(ROSTER)) {
            assertThat(roster.absolute(5)).isTrue();
            assertThat(roster.getInt("credits")).isEqualTo(25);

            try (Connection kiosk = dataSource.getConnection();
                 PreparedStatement spend = kiosk.prepareStatement(
                         "UPDATE members SET credits = credits - ? WHERE name = ?")) {
                spend.setInt(1, 7);
                spend.setString(2, "Elif");
                assertThat(spend.executeUpdate()).isEqualTo(1);
            }

            // The scroll-insensitive copy is stale until the desk asks for the current row.
            assertThat(roster.getInt("credits")).isEqualTo(25);
            roster.refreshRow();
            assertThat(roster.getInt("credits")).isEqualTo(18);

            // An update built on the refreshed value does not lose the kiosk's change.
            roster.updateInt("credits", roster.getInt("credits") + 2);
            roster.updateRow();
        }
        assertThat(rosterSnapshot()).containsEntry("Elif", "silver:20");
    }

    @Test
    void aggregateReportIsReadOnlyEvenWhenUpdatabilityIsRequested() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement(
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             ResultSet report = statement.executeQuery(
                     "SELECT tier, SUM(credits) AS total FROM members GROUP BY tier ORDER BY tier")) {
            assertThat(report.getConcurrency()).isEqualTo(ResultSet.CONCUR_READ_ONLY);
            Map<String, Integer> totals = new LinkedHashMap<>();
            while (report.next()) {
                totals.put(report.getString("tier"), report.getInt("total"));
            }
            assertThat(totals).containsExactly(Map.entry("basic", 5), Map.entry("gold", 52),
                    Map.entry("silver", 25));

            assertThat(report.first()).isTrue();
            assertThatThrownBy(() -> report.updateInt("total", 0)).isInstanceOf(NotUpdatable.class);
            assertThatThrownBy(report::moveToInsertRow).isInstanceOf(NotUpdatable.class);
        }
    }

    /// Reads every member as `name -> tier:credits`, ordered by name.
    private static Map<String, String> rosterSnapshot() throws SQLException {
        Map<String, String> members = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name, tier, credits FROM members ORDER BY name")) {
            while (rows.next()) {
                members.put(rows.getString("name"), rows.getString("tier") + ":" + rows.getInt("credits"));
            }
        }
        return members;
    }

    private static LocalDate joinedOn(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT joined FROM members WHERE name = ?")) {
            query.setString(1, name);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getObject("joined", LocalDate.class);
            }
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
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
