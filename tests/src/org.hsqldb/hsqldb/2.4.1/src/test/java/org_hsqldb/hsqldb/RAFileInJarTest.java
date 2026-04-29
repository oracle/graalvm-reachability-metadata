/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RAFileInJarTest {
    @TempDir
    Path tempDir;

    @Test
    public void readsCachedTableDataThroughResDatabaseResources() throws Exception {
        String databaseName = uniqueDatabaseName();
        Path databasePath = tempDir.resolve(databaseName);

        createCachedDatabase(databasePath);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new DatabaseResourceClassLoader(originalClassLoader, tempDir));

        try {
            assertCachedTableContent(resDatabaseUrl(databaseName));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static String uniqueDatabaseName() {
        return ("rafileinjarcoverage" + Long.toHexString(System.nanoTime())).toLowerCase(Locale.ROOT);
    }

    private static void createCachedDatabase(Path databasePath) throws Exception {
        try (Connection connection = openConnection(fileDatabaseUrl(databasePath));
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE CACHED TABLE entries (id INTEGER PRIMARY KEY, name VARCHAR(32))");
            statement.execute("INSERT INTO entries VALUES (1, 'resource-backed')");
            statement.execute("CHECKPOINT");
            statement.execute("SHUTDOWN");
        }

        assertTrue(Files.exists(databasePath.resolveSibling(databasePath.getFileName() + ".data")));
    }

    private static void assertCachedTableContent(String url) throws Exception {
        try (Connection connection = openConnection(url);
                PreparedStatement query = connection.prepareStatement("SELECT name FROM entries WHERE id = ?")) {
            query.setInt(1, 1);

            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("resource-backed", resultSet.getString(1));
            }
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String fileDatabaseUrl(Path databasePath) {
        return "jdbc:hsqldb:file:" + databasePath.toAbsolutePath().toString().replace('\\', '/') + ";shutdown=true";
    }

    private static String resDatabaseUrl(String databaseName) {
        return "jdbc:hsqldb:res:" + databaseName + ";ifexists=true;shutdown=true";
    }

    private static final class DatabaseResourceClassLoader extends ClassLoader {
        private final Path resourceRoot;

        private DatabaseResourceClassLoader(ClassLoader parent, Path resourceRoot) {
            super(parent);
            this.resourceRoot = resourceRoot;
        }

        @Override
        public URL getResource(String name) {
            Path resource = resourceRoot.resolve(normalizeResourceName(name));

            if (Files.isRegularFile(resource)) {
                try {
                    return resource.toUri().toURL();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to expose database resource " + name, e);
                }
            }

            return super.getResource(name);
        }

        private static String normalizeResourceName(String name) {
            return name.startsWith("/") ? name.substring(1) : name;
        }
    }
}
