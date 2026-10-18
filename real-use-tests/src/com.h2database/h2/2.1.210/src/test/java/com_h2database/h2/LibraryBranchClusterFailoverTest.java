/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.h2.api.ErrorCode;
import org.h2.tools.CreateCluster;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H2 high-availability cluster mode: two TCP server nodes joined with
 * `org.h2.tools.CreateCluster`, a cluster JDBC URL that replicates every write and transaction to
 * both nodes, the `CLUSTER` database setting that guards a clustered database against standalone
 * connections, automatic failover to the surviving node when one server stops, and rebuilding
 * the cluster after the failed node comes back.
 *
 * A library chain keeps its loan desk database on two branch servers: the desk writes through
 * the cluster URL, keeps lending while one branch server is down, and the operator rejoins the
 * repaired branch so both copies hold every loan again.
 */
public class LibraryBranchClusterFailoverTest {

    private static final String USER = "sa";

    private static final String PASSWORD = "loan-desk";

    private static final String SCHEMA = """
            CREATE TABLE book (
                isbn VARCHAR(17) PRIMARY KEY,
                title VARCHAR(80) NOT NULL
            );
            CREATE TABLE loan (
                id INT AUTO_INCREMENT PRIMARY KEY,
                isbn VARCHAR(17) NOT NULL REFERENCES book (isbn),
                member VARCHAR(40) NOT NULL
            );
            INSERT INTO book VALUES
                ('978-0-13-468599-1', 'Effective Java'),
                ('978-1-61729-454-7', 'Kotlin in Action'),
                ('978-0-596-00712-6', 'Head First Design Patterns');
            INSERT INTO loan (isbn, member) VALUES ('978-0-13-468599-1', 'Ines');
            """;

    @TempDir
    Path branchesDir;

    @Test
    void clusterReplicatesLoansAndFailsOverWhenOneBranchStops() throws IOException, SQLException {
        Path northDir = Files.createDirectories(branchesDir.resolve("north"));
        Path southDir = Files.createDirectories(branchesDir.resolve("south"));
        Server north = startNode(northDir);
        Server south = startNode(southDir);
        Server repaired = null;
        try {
            try (Connection setup = DriverManager.getConnection(nodeUrl(north), USER, PASSWORD);
                    Statement statement = setup.createStatement()) {
                statement.execute(SCHEMA);
            }
            String servers = serverList(north, south);
            new CreateCluster().execute(nodeUrl(north), nodeUrl(south), USER, PASSWORD, servers);
            assertRunsClustered(north);
            assertRunsClustered(south);

            try (Connection desk = DriverManager.getConnection(clusterUrl(servers), USER, PASSWORD)) {
                assertEquals("'" + servers + "'", clusterSetting(desk));
                assertEquals(List.of("978-0-13-468599-1 Ines"), loans(desk));
                assertEquals(1, lend(desk, "978-1-61729-454-7", "Marek"));
                desk.setAutoCommit(false);
                assertEquals(1, lend(desk, "978-0-596-00712-6", "Noor"));
                desk.rollback();
                assertEquals(1, lend(desk, "978-0-596-00712-6", "Olga"));
                desk.commit();
                desk.setAutoCommit(true);
                SQLException unknownBook = assertThrows(SQLException.class,
                        () -> lend(desk, "978-0-00-000000-0", "Pavel"));
                assertEquals(ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1,
                        unknownBook.getErrorCode());

                south.stop();
                assertEquals(1, lend(desk, "978-0-13-468599-1", "Rosa"));
                assertEquals("''", clusterSetting(desk));
            }
            try (Connection alone = DriverManager.getConnection(nodeUrl(north), USER, PASSWORD)) {
                assertEquals(List.of(
                        "978-0-13-468599-1 Ines",
                        "978-1-61729-454-7 Marek",
                        "978-0-596-00712-6 Olga",
                        "978-0-13-468599-1 Rosa"), loans(alone));
            }

            Server southAgain = startNode(southDir);
            repaired = southAgain;
            assertRunsClustered(southAgain);
            String rejoined = serverList(north, southAgain);
            new CreateCluster().execute(nodeUrl(north), nodeUrl(southAgain), USER, PASSWORD, rejoined);
            assertRunsClustered(north);
            try (Connection desk = DriverManager.getConnection(clusterUrl(rejoined), USER, PASSWORD)) {
                assertEquals("'" + rejoined + "'", clusterSetting(desk));
                assertEquals(1, lend(desk, "978-1-61729-454-7", "Sven"));
            }

            north.stop();
            try (Connection desk = DriverManager.getConnection(clusterUrl(rejoined), USER, PASSWORD)) {
                assertEquals("''", clusterSetting(desk));
                assertEquals(List.of(
                        "978-0-13-468599-1 Ines",
                        "978-1-61729-454-7 Marek",
                        "978-0-596-00712-6 Olga",
                        "978-0-13-468599-1 Rosa",
                        "978-1-61729-454-7 Sven"), loans(desk));
            }
            try (Connection alone = DriverManager.getConnection(nodeUrl(southAgain), USER, PASSWORD)) {
                assertEquals(5, loans(alone).size());
            }
        } finally {
            north.stop();
            south.stop();
            if (repaired != null) {
                repaired.stop();
            }
        }
    }

    /// A clustered node refuses a plain connection, so no client can write to one copy only.
    private static void assertRunsClustered(Server node) {
        SQLException refused = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(nodeUrl(node), USER, PASSWORD).close());
        assertEquals(ErrorCode.CLUSTER_ERROR_DATABASE_RUNS_CLUSTERED_1, refused.getErrorCode());
    }

    private static Server startNode(Path baseDir) throws SQLException {
        return Server.createTcpServer(
                "-tcpPort", "0",
                "-baseDir", baseDir.toString(),
                "-ifNotExists").start();
    }

    private static String serverList(Server first, Server second) {
        return "localhost:" + first.getPort() + ",localhost:" + second.getPort();
    }

    private static String nodeUrl(Server node) {
        return "jdbc:h2:tcp://localhost:" + node.getPort() + "/loans";
    }

    private static String clusterUrl(String servers) {
        return "jdbc:h2:tcp://" + servers + "/loans";
    }

    private static int lend(Connection connection, String isbn, String member) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO loan (isbn, member) VALUES (?, ?)")) {
            insert.setString(1, isbn);
            insert.setString(2, member);
            return insert.executeUpdate();
        }
    }

    private static String clusterSetting(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet settings = statement.executeQuery("""
                        SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS
                        WHERE SETTING_NAME = 'CLUSTER'
                        """)) {
            settings.next();
            return settings.getString(1);
        }
    }

    private static List<String> loans(Connection connection) throws SQLException {
        List<String> loans = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT isbn, member FROM loan ORDER BY id")) {
            while (rows.next()) {
                loans.add(rows.getString(1) + " " + rows.getString(2));
            }
        }
        return loans;
    }
}
