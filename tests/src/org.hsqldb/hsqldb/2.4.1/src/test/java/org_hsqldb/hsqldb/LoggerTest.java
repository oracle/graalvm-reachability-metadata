/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.lib.FileAccess;
import org.hsqldb.persist.RandomAccessInterface;
import org.junit.jupiter.api.Test;

public class LoggerTest {
    private static final String STORAGE_KEY = "logger-storage-key";

    @Test
    void openInstantiatesConfiguredFileAccessFromContextLoader() throws Exception {
        LoggerFileAccess.reset();

        String databaseName = "logger_custom_access_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:file:" + databaseName
                + ";fileaccess_class_name=" + LoggerTest.LoggerFileAccess.class.getName()
                + ";storage_class_name=" + RandomAccessInterface.class.getName()
                + ";storage_key=" + STORAGE_KEY
                + ";hsqldb.lock_file=false"
                + ";shutdown=true";

        try (Connection connection = openConnection(url);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("VALUES 42")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(42);
        }

        assertThat(LoggerTest.LoggerFileAccess.instances()).isEqualTo(1);
        assertThat(LoggerTest.LoggerFileAccess.lastStorageKey()).isEqualTo(STORAGE_KEY);
    }

    @Test
    void openFallsBackWhenContextLoaderCannotResolveConfiguredFileAccess() throws Exception {
        String fileAccessClassName = "org.hsqldb.lib.FileUtil$FileAccessRes";
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassHidingLoader classHidingLoader = new ClassHidingLoader(originalClassLoader, fileAccessClassName);
        String databaseName = "logger_fallback_access_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:mem:" + databaseName
                + ";fileaccess_class_name=" + fileAccessClassName
                + ";storage_class_name=" + RandomAccessInterface.class.getName()
                + ";storage_key=" + STORAGE_KEY
                + ";shutdown=true";

        try {
            Thread.currentThread().setContextClassLoader(classHidingLoader);

            try (Connection connection = openConnection(url);
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("VALUES 7")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(7);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(classHidingLoader.hiddenLoadAttempts()).isEqualTo(1);
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    public static final class LoggerFileAccess implements FileAccess {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicReference<Object> LAST_STORAGE_KEY = new AtomicReference<>();
        private static final Map<String, byte[]> ELEMENTS = new ConcurrentHashMap<>();

        public LoggerFileAccess(Object storageKey) {
            INSTANCES.incrementAndGet();
            LAST_STORAGE_KEY.set(storageKey);
        }

        static void reset() {
            INSTANCES.set(0);
            LAST_STORAGE_KEY.set(null);
            ELEMENTS.clear();
        }

        static int instances() {
            return INSTANCES.get();
        }

        static Object lastStorageKey() {
            return LAST_STORAGE_KEY.get();
        }

        @Override
        public InputStream openInputStreamElement(String streamName) throws IOException {
            byte[] content = ELEMENTS.get(streamName);

            if (content == null) {
                throw new FileNotFoundException(streamName);
            }

            return new ByteArrayInputStream(content);
        }

        @Override
        public OutputStream openOutputStreamElement(String streamName, boolean append) {
            return new StoredOutputStream(streamName, append);
        }

        @Override
        public boolean isStreamElement(String elementName) {
            return ELEMENTS.containsKey(elementName);
        }

        @Override
        public void createParentDirs(String filename) {
        }

        @Override
        public void removeElement(String filename) {
            ELEMENTS.remove(filename);
        }

        @Override
        public void renameElement(String oldName, String newName, boolean copyIfFailed) {
            byte[] content = ELEMENTS.remove(oldName);

            if (content != null) {
                ELEMENTS.put(newName, content);
            }
        }

        @Override
        public FileAccess.FileSync getFileSync(OutputStream os) throws IOException {
            return os::flush;
        }

        private static final class StoredOutputStream extends ByteArrayOutputStream {
            private final String streamName;

            private StoredOutputStream(String streamName, boolean append) {
                this.streamName = streamName;

                if (append) {
                    byte[] content = ELEMENTS.get(streamName);

                    if (content != null) {
                        write(content, 0, content.length);
                    }
                }
            }

            @Override
            public void close() throws IOException {
                super.close();
                ELEMENTS.put(streamName, toByteArray());
            }
        }
    }

    private static final class ClassHidingLoader extends ClassLoader {
        private final String hiddenClassName;
        private int hiddenLoadAttempts;

        private ClassHidingLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        int hiddenLoadAttempts() {
            return hiddenLoadAttempts;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                hiddenLoadAttempts++;

                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name, resolve);
        }
    }
}
