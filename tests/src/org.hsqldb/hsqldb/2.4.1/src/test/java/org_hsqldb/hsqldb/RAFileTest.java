/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RAFileTest {
    private static final AtomicInteger STORAGE_OPEN_COUNT = new AtomicInteger();

    @TempDir
    Path tempDir;

    @Test
    public void opensCachedTableThroughConfiguredStorageClass() throws Exception {
        STORAGE_OPEN_COUNT.set(0);

        String url = storedDatabaseUrl(tempDir.resolve("stored-db"));
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new FallbackClassLoader(originalClassLoader));

        try {
            createCachedTable(url);
            assertCachedTableContent(url);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertTrue(STORAGE_OPEN_COUNT.get() > 0);
    }

    private static void createCachedTable(String url) throws Exception {
        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE CACHED TABLE entries (id INTEGER PRIMARY KEY, name VARCHAR(32))");

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO entries VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "stored-value");
                assertEquals(1, insert.executeUpdate());
            }

            statement.execute("CHECKPOINT");
        }
    }

    private static void assertCachedTableContent(String url) throws Exception {
        try (Connection connection = openConnection(url);
                PreparedStatement query = connection.prepareStatement("SELECT name FROM entries WHERE id = ?")) {
            query.setInt(1, 1);

            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("stored-value", resultSet.getString(1));
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

    private static String storedDatabaseUrl(Path databasePath) {
        String normalizedPath = databasePath.toAbsolutePath().toString().replace(File.separatorChar, '/');

        return "jdbc:hsqldb:file:" + normalizedPath
                + ";fileaccess_class_name=" + LocalFileAccess.class.getName()
                + ";storage_class_name=" + LocalStorage.class.getName()
                + ";storage_key=test-key;shutdown=true";
    }

    private static final class FallbackClassLoader extends ClassLoader {
        private FallbackClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (LocalFileAccess.class.getName().equals(name) || LocalStorage.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name);
        }
    }

    public static final class LocalFileAccess implements FileAccess {
        public LocalFileAccess(Object ignoredStorageKey) {
            // Constructor signature required by the HSQLDB stored-file extension point.
        }

        @Override
        public InputStream openInputStreamElement(String streamName) throws IOException {
            return new FileInputStream(streamName);
        }

        @Override
        public OutputStream openOutputStreamElement(String streamName, boolean append) throws IOException {
            createParentDirs(streamName);
            return new FileOutputStream(streamName, append);
        }

        @Override
        public boolean isStreamElement(String elementName) {
            return new File(elementName).exists();
        }

        @Override
        public void createParentDirs(String filename) {
            File parent = new File(filename).getParentFile();

            if (parent != null) {
                parent.mkdirs();
            }
        }

        @Override
        public void removeElement(String filename) {
            new File(filename).delete();
        }

        @Override
        public void renameElement(String oldName, String newName, boolean copyIfFailed) {
            File oldFile = new File(oldName);
            File newFile = new File(newName);

            createParentDirs(newName);

            if (oldFile.renameTo(newFile) || !copyIfFailed || !oldFile.exists()) {
                return;
            }

            try {
                Files.copy(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                oldFile.delete();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to rename " + oldName + " to " + newName, e);
            }
        }

        @Override
        public FileAccess.FileSync getFileSync(OutputStream os) throws IOException {
            if (os instanceof FileOutputStream) {
                FileOutputStream fileOutputStream = (FileOutputStream) os;
                return fileOutputStream.getFD()::sync;
            }

            return os::flush;
        }
    }

    public static final class LocalStorage implements Storage {
        private final RandomAccessFile file;
        private final boolean readOnly;

        public LocalStorage(String name, Boolean requestedReadOnly, Object ignoredStorageKey) throws IOException {
            this.readOnly = Boolean.TRUE.equals(requestedReadOnly);
            File filePath = new File(name);
            File parent = filePath.getParentFile();

            if (parent != null) {
                parent.mkdirs();
            }

            this.file = new RandomAccessFile(filePath, this.readOnly ? "r" : "rw");
            STORAGE_OPEN_COUNT.incrementAndGet();
        }

        @Override
        public long length() throws IOException {
            return file.length();
        }

        @Override
        public void seek(long position) throws IOException {
            file.seek(position);
        }

        @Override
        public long getFilePointer() throws IOException {
            return file.getFilePointer();
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public void read(byte[] b, int offset, int length) throws IOException {
            file.readFully(b, offset, length);
        }

        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
            file.write(b, offset, length);
        }

        @Override
        public int readInt() throws IOException {
            return file.readInt();
        }

        @Override
        public void writeInt(int i) throws IOException {
            file.writeInt(i);
        }

        @Override
        public long readLong() throws IOException {
            return file.readLong();
        }

        @Override
        public void writeLong(long i) throws IOException {
            file.writeLong(i);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public boolean wasNio() {
            return false;
        }
    }
}
