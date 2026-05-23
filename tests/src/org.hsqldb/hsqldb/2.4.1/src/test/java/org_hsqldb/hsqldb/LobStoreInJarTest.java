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
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.Database;
import org.hsqldb.Session;
import org.hsqldb.jdbc.JDBCConnection;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.persist.LobStoreInJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LobStoreInJarTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsLobBlockFromContextClassLoaderResource() throws Exception {
        String databasePath = temporaryDirectory.resolve("lob_store_in_jar_db").toString();
        String url = "jdbc:hsqldb:file:" + databasePath
                + ";hsqldb.lock_file=false";
        byte[] expectedBlock = "context loader".getBytes(StandardCharsets.US_ASCII);

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            Database database = databaseSession(connection).getDatabase();
            String lobResourceName = database.getPath() + ".lobs";
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            ResourceProvidingClassLoader resourceClassLoader = new ResourceProvidingClassLoader(
                    originalClassLoader,
                    lobResourceName,
                    expectedBlock);

            try {
                Thread.currentThread().setContextClassLoader(resourceClassLoader);

                LobStoreInJar lobStore = new LobStoreInJar(database, expectedBlock.length);

                try {
                    assertThat(lobStore.getBlockSize()).isEqualTo(expectedBlock.length);
                    assertThat(lobStore.getBlockBytes(0, 1)).isEqualTo(expectedBlock);
                } finally {
                    lobStore.close();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

            assertThat(resourceClassLoader.lastRequestedName()).isEqualTo(lobResourceName);
            assertThat(resourceClassLoader.resourceRequests()).isGreaterThanOrEqualTo(1);

            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void resDatabaseReadsBlobFromContextClassLoaderResource() throws Exception {
        String sourceDatabasePath = temporaryDirectory.resolve("lob_store_in_jar_source").toString();
        String sourceUrl = "jdbc:hsqldb:file:" + sourceDatabasePath
                + ";hsqldb.lock_file=false";
        byte[] expectedBytes = largeBlobBytes();

        createFileDatabaseWithBlob(sourceUrl, expectedBytes);

        String resourceDatabasePath = "/org_hsqldb/hsqldb/generated-lob-store-in-jar-db";
        Map<String, byte[]> resources = databaseResources(sourceDatabasePath, resourceDatabasePath);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceProvidingClassLoader resourceClassLoader = new ResourceProvidingClassLoader(
                originalClassLoader,
                resources);

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            String resourceUrl = "jdbc:hsqldb:res:" + resourceDatabasePath.substring(1);

            try (Connection connection = openConnection(resourceUrl);
                    Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("SELECT payload FROM lob_items WHERE id = 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getBytes(1)).isEqualTo(expectedBytes);
                }

                statement.execute("SHUTDOWN");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(resourceClassLoader.requested(resourceDatabasePath + ".lobs")).isTrue();
    }

    private static byte[] largeBlobBytes() {
        byte[] bytes = new byte[8192];

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ('A' + i % 26);
        }

        return bytes;
    }

    private static void createFileDatabaseWithBlob(String url, byte[] expectedBytes) throws Exception {
        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lob_items (id INTEGER PRIMARY KEY, payload BLOB)");

            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO lob_items VALUES (?, ?)")) {
                preparedStatement.setInt(1, 1);
                preparedStatement.setBytes(2, expectedBytes);
                preparedStatement.executeUpdate();
            }

            statement.execute("CHECKPOINT");
            statement.execute("SHUTDOWN");
        }
    }

    private static Map<String, byte[]> databaseResources(
            String sourceDatabasePath,
            String resourceDatabasePath) throws Exception {
        Map<String, byte[]> resources = new HashMap<>();

        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".properties");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".script");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".lobs");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".log");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".data");
        addDatabaseResource(resources, sourceDatabasePath, resourceDatabasePath, ".backup");

        return resources;
    }

    private static void addDatabaseResource(
            Map<String, byte[]> resources,
            String sourceDatabasePath,
            String resourceDatabasePath,
            String extension) throws Exception {
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
        private final AtomicInteger resourceRequests = new AtomicInteger();
        private final AtomicReference<String> lastRequestedName = new AtomicReference<>();

        ResourceProvidingClassLoader(ClassLoader parent, String resourceName, byte[] resourceBytes) {
            this(parent, Map.of(resourceName, resourceBytes.clone()));
        }

        ResourceProvidingClassLoader(ClassLoader parent, Map<String, byte[]> resources) {
            super(parent);
            this.resources = new HashMap<>(resources);
        }

        @Override
        public URL getResource(String name) {
            requestedNames.add(name);
            lastRequestedName.set(name);

            byte[] resourceBytes = resources.get(name);

            if (resourceBytes == null) {
                return super.getResource(name);
            }

            resourceRequests.incrementAndGet();

            try {
                return new URL(null, "memory://lob-store-in-jar/" + name,
                        new ResourceStreamHandler(resourceBytes));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        boolean requested(String name) {
            return requestedNames.contains(name);
        }

        int resourceRequests() {
            return resourceRequests.get();
        }

        String lastRequestedName() {
            return lastRequestedName.get();
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
