/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hsqldb.Database;
import org.hsqldb.Session;
import org.hsqldb.jdbc.JDBCConnection;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.persist.DataFileCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RAFileInJarTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resDatabaseReadsCachedTableDataFromContextClassLoaderResource() throws Exception {
        String sourceDatabasePath = temporaryDirectory.resolve("rafile_in_jar_source").toString();
        String sourceUrl = "jdbc:hsqldb:file:" + sourceDatabasePath
                + ";hsqldb.lock_file=false";
        String expectedPayload = "cached table row from resource database";

        createFileDatabaseWithCachedTable(sourceUrl, expectedPayload);

        String resourceDatabasePath = "/org_hsqldb/hsqldb/generated-rafile-in-jar-db-"
                + Long.toUnsignedString(System.nanoTime());
        Map<String, byte[]> resources = databaseResources(sourceDatabasePath, resourceDatabasePath);
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ResourceProvidingClassLoader resourceClassLoader = new ResourceProvidingClassLoader(
                originalClassLoader,
                resources);

        try {
            currentThread.setContextClassLoader(resourceClassLoader);

            String resourceUrl = "jdbc:hsqldb:res:" + resourceDatabasePath.substring(1);

            try (Connection connection = openConnection(resourceUrl);
                    Statement statement = connection.createStatement()) {
                Database database = databaseSession(connection).getDatabase();
                DataFileCache dataFileCache = new DataFileCache(database, database.getPath());

                try {
                    dataFileCache.open(true);
                } finally {
                    dataFileCache.release();
                }

                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT payload FROM cached_items WHERE id = 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString(1)).isEqualTo(expectedPayload);
                }

                statement.execute("SHUTDOWN");
            }
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(resourceClassLoader.requested(resourceDatabasePath + ".data")).isTrue();
        assertThat(resourceClassLoader.streamRequested(resourceDatabasePath + ".data")).isTrue();
    }

    private static void createFileDatabaseWithCachedTable(String url, String expectedPayload) throws Exception {
        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE CACHED TABLE cached_items (id INTEGER PRIMARY KEY, payload VARCHAR(128))");

            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO cached_items VALUES (?, ?)")) {
                preparedStatement.setInt(1, 1);
                preparedStatement.setString(2, expectedPayload);
                preparedStatement.executeUpdate();
            }

            statement.execute("CHECKPOINT");
            statement.execute("SHUTDOWN");
        }
    }

    private static Map<String, byte[]> databaseResources(
            String sourceDatabasePath,
            String resourceDatabasePath) throws IOException {
        Map<String, byte[]> resources = new HashMap<>();

        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".properties");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".script");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".data");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".backup");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".log");

        assertThat(resources).containsKey(resourceDatabasePath + ".data");

        return resources;
    }

    private static void addDatabaseResource(
            Map<String, byte[]> resources,
            String sourceDatabasePath,
            String resourceDatabasePath,
            String extension) throws IOException {
        Path sourcePath = Path.of(sourceDatabasePath + extension);

        if (Files.exists(sourcePath)) {
            byte[] bytes = Files.readAllBytes(sourcePath);

            resources.put(resourceDatabasePath + extension, bytes);
            resources.put(resourceDatabasePath.substring(1) + extension, bytes);
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static Session databaseSession(Connection connection) throws Exception {
        JDBCConnection hsqldbConnection = connection.unwrap(JDBCConnection.class);

        return (Session) hsqldbConnection.getSession();
    }

    private static final class ResourceProvidingClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;
        private final Set<String> requestedNames = ConcurrentHashMap.newKeySet();
        private final Set<String> streamRequestedNames = ConcurrentHashMap.newKeySet();

        ResourceProvidingClassLoader(ClassLoader parent, Map<String, byte[]> resources) {
            super(parent);
            this.resources = new HashMap<>(resources);
        }

        @Override
        public URL getResource(String name) {
            requestedNames.add(name);

            byte[] resourceBytes = resources.get(name);

            if (resourceBytes == null) {
                return super.getResource(name);
            }

            try {
                return new URL(null, "memory://rafile-in-jar/" + name,
                        new ResourceStreamHandler(resourceBytes));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedNames.add(name);

            byte[] resourceBytes = resources.get(name);

            if (resourceBytes == null) {
                return super.getResourceAsStream(name);
            }

            streamRequestedNames.add(name);

            return new ByteArrayInputStream(resourceBytes);
        }

        boolean requested(String name) {
            return requestedNames.contains(name);
        }

        boolean streamRequested(String name) {
            return streamRequestedNames.contains(name);
        }
    }

    private static final class ResourceStreamHandler extends URLStreamHandler {
        private final byte[] resourceBytes;

        ResourceStreamHandler(byte[] resourceBytes) {
            this.resourceBytes = resourceBytes.clone();
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(resourceBytes);
                }
            };
        }
    }
}
