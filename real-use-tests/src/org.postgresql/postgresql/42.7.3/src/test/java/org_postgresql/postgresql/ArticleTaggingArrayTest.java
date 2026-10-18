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
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tags and ranks blog articles with PostgreSQL array columns bound and read through the driver's
 * SQL array support (`Connection.createArrayOf`, `PGConnection.createArrayOf` and `java.sql.Array`).
 *
 * An editor stores each article's tags as `text[]`, its reader ratings as `int4[]` and its
 * weekly page views as a two-dimensional `int4[][]`; the site then finds articles by a single tag
 * and by overlapping tags, aggregates tags per author with `array_agg`, and reads every array
 * back as typed Java arrays, as a slice and as the element result set the driver exposes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleTaggingArrayTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "blog";

    private static final String USER = "editor";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

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

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE articles (
                        slug         text PRIMARY KEY,
                        author       text NOT NULL,
                        tags         text[] NOT NULL,
                        ratings      int4[],
                        weekly_views int4[][]
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void tagsRanksAndSearchesArticles() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO articles (slug, author, tags, ratings, weekly_views)
                    VALUES (?, ?, ?, ?, ?)""")) {
                insert.setString(1, "native-java");
                insert.setString(2, "mila");
                insert.setArray(3, connection.createArrayOf("text", new String[] {"java", "graalvm", "aot"}));
                insert.setArray(4, pgConnection.createArrayOf("int4", new int[] {5, 4, 5}));
                insert.setObject(5, new int[][] {{120, 140}, {90, 160}});
                insert.addBatch();

                insert.setString(1, "sql-arrays");
                insert.setString(2, "mila");
                insert.setArray(3, connection.createArrayOf("text", new String[] {"postgresql", "sql", null}));
                insert.setArray(4, connection.createArrayOf("int4", new Integer[] {3, null, 4}));
                insert.setNull(5, Types.ARRAY);
                insert.addBatch();

                insert.setString(1, "jdbc-pools");
                insert.setString(2, "noah");
                insert.setObject(3, new String[] {"java", "postgresql", "pooling"});
                insert.setNull(4, Types.ARRAY);
                insert.setNull(5, Types.ARRAY);
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1, 1);
            }

            try (PreparedStatement byTag = connection.prepareStatement(
                    "SELECT slug FROM articles WHERE ? = ANY(tags) ORDER BY slug")) {
                byTag.setString(1, "java");
                assertThat(slugs(byTag)).containsExactly("jdbc-pools", "native-java");
            }

            try (PreparedStatement overlapping = connection.prepareStatement(
                    "SELECT slug FROM articles WHERE tags && ? ORDER BY slug")) {
                overlapping.setArray(1, connection.createArrayOf("text", new String[] {"sql", "aot"}));
                assertThat(slugs(overlapping)).containsExactly("native-java", "sql-arrays");
            }

            try (PreparedStatement article = connection.prepareStatement(
                    "SELECT tags, ratings, weekly_views FROM articles WHERE slug = ?")) {
                article.setString(1, "native-java");
                try (ResultSet rows = article.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Array tags = rows.getArray("tags");
                    assertThat(tags.getBaseType()).isEqualTo(Types.VARCHAR);
                    assertThat(tags.getBaseTypeName()).isEqualTo("text");
                    assertThat((String[]) tags.getArray()).containsExactly("java", "graalvm", "aot");
                    assertThat((String[]) tags.getArray(2, 2)).containsExactly("graalvm", "aot");

                    Map<Integer, String> positions = new LinkedHashMap<>();
                    try (ResultSet elements = tags.getResultSet()) {
                        while (elements.next()) {
                            positions.put(elements.getInt(1), elements.getString(2));
                        }
                    }
                    assertThat(positions).containsExactly(
                            Map.entry(1, "java"), Map.entry(2, "graalvm"), Map.entry(3, "aot"));
                    tags.free();

                    Array ratings = rows.getArray("ratings");
                    assertThat(ratings.getBaseType()).isEqualTo(Types.INTEGER);
                    assertThat((Integer[]) ratings.getArray()).containsExactly(5, 4, 5);

                    Integer[][] weeklyViews = (Integer[][]) rows.getArray("weekly_views").getArray();
                    assertThat(weeklyViews).hasDimensions(2, 2);
                    assertThat(weeklyViews[0]).containsExactly(120, 140);
                    assertThat(weeklyViews[1]).containsExactly(90, 160);
                }

                article.setString(1, "sql-arrays");
                try (ResultSet rows = article.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat((String[]) rows.getArray("tags").getArray()).containsExactly("postgresql", "sql", null);
                    assertThat((Integer[]) rows.getArray("ratings").getArray()).containsExactly(3, null, 4);
                    assertThat(rows.getArray("weekly_views")).isNull();
                    assertThat(rows.wasNull()).isTrue();
                }
            }

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("""
                            SELECT author, array_agg(DISTINCT tag ORDER BY tag) AS tags,
                                   array_length(array_agg(DISTINCT slug), 1) AS articles
                            FROM articles, unnest(tags) AS tag
                            WHERE tag IS NOT NULL
                            GROUP BY author ORDER BY author""")) {
                Map<String, List<String>> tagsByAuthor = new LinkedHashMap<>();
                Map<String, Integer> articlesByAuthor = new LinkedHashMap<>();
                while (rows.next()) {
                    String author = rows.getString("author");
                    tagsByAuthor.put(author, List.of((String[]) rows.getArray("tags").getArray()));
                    articlesByAuthor.put(author, rows.getInt("articles"));
                }
                assertThat(tagsByAuthor).containsExactly(
                        Map.entry("mila", List.of("aot", "graalvm", "java", "postgresql", "sql")),
                        Map.entry("noah", List.of("java", "pooling", "postgresql")));
                assertThat(articlesByAuthor).containsExactly(Map.entry("mila", 2), Map.entry("noah", 1));
            }

            try (PreparedStatement retag = connection.prepareStatement(
                    "UPDATE articles SET tags = array_append(array_remove(tags, ?), ?) WHERE slug = ? RETURNING tags")) {
                retag.setString(1, "pooling");
                retag.setString(2, "hikaricp");
                retag.setString(3, "jdbc-pools");
                try (ResultSet rows = retag.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("tags", Array.class).getArray())
                            .isEqualTo(new String[] {"java", "postgresql", "hikaricp"});
                }
            }
        }
    }

    private static List<String> slugs(PreparedStatement query) throws SQLException {
        List<String> slugs = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                slugs.add(rows.getString("slug"));
            }
        }
        return slugs;
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
