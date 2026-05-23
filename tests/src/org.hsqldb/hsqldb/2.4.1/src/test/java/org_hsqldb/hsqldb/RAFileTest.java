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
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.Database;
import org.hsqldb.Session;
import org.hsqldb.jdbc.JDBCConnection;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.lib.FileAccess;
import org.hsqldb.persist.LobStoreRAFile;
import org.hsqldb.persist.RandomAccessInterface;
import org.junit.jupiter.api.Test;

public class RAFileTest {
    private static final String STORAGE_KEY = "rafile-storage-key";

    @Test
    void lobStoreUsesConfiguredRandomAccessStorageWhenContextLoaderFallsBack() throws Exception {
        InMemoryFileAccess.reset();
        InMemoryRandomAccessStorage.reset();

        String storageClassName = InMemoryRandomAccessStorage.class.getName();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassProvidingLoader classProvidingLoader = new ClassProvidingLoader(
                originalClassLoader,
                storageClassName,
                InMemoryRandomAccessStorage.class);
        ClassHidingLoader classHidingLoader = new ClassHidingLoader(originalClassLoader, storageClassName);
        String databaseName = "rafile_custom_storage_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:file:" + databaseName
                + ";fileaccess_class_name=" + InMemoryFileAccess.class.getName()
                + ";storage_class_name=" + storageClassName
                + ";storage_key=" + STORAGE_KEY
                + ";hsqldb.lock_file=false"
                + ";shutdown=true";

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            Database database = databaseSession(connection).getDatabase();
            byte[] expectedBlock = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

            Thread.currentThread().setContextClassLoader(classProvidingLoader);
            writeAndReadLobBlock(database, expectedBlock);

            InMemoryRandomAccessStorage.resetObservations();
            classHidingLoader.reset();
            Thread.currentThread().setContextClassLoader(classHidingLoader);

            writeAndReadLobBlock(database, expectedBlock);

            Thread.currentThread().setContextClassLoader(originalClassLoader);
            statement.execute("SHUTDOWN");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(classProvidingLoader.providedLoadAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(classHidingLoader.hiddenLoadAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(InMemoryRandomAccessStorage.instances()).isGreaterThanOrEqualTo(1);
        assertThat(InMemoryRandomAccessStorage.lastName()).endsWith(".lobs");
        assertThat(InMemoryRandomAccessStorage.lastReadOnly()).isFalse();
        assertThat(InMemoryRandomAccessStorage.lastStorageKey()).isEqualTo(STORAGE_KEY);
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static void writeAndReadLobBlock(Database database, byte[] expectedBlock) {
        LobStoreRAFile lobStore = new LobStoreRAFile(database, expectedBlock.length);

        lobStore.setBlockBytes(expectedBlock, 0, 1);

        assertThat(lobStore.getBlockBytes(0, 1)).isEqualTo(expectedBlock);
        lobStore.close();
    }

    private static Session databaseSession(Connection connection) throws Exception {
        JDBCConnection hsqldbConnection = connection.unwrap(JDBCConnection.class);

        return (Session) hsqldbConnection.getSession();
    }

    public static final class InMemoryRandomAccessStorage implements RandomAccessInterface {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicReference<String> LAST_NAME = new AtomicReference<>();
        private static final AtomicReference<Boolean> LAST_READ_ONLY = new AtomicReference<>();
        private static final AtomicReference<Object> LAST_STORAGE_KEY = new AtomicReference<>();
        private static final Map<String, byte[]> FILES = new ConcurrentHashMap<>();

        private final String name;
        private final boolean readOnly;
        private long position;

        public InMemoryRandomAccessStorage(String name, Boolean readOnly, Object storageKey) {
            this.name = name;
            this.readOnly = readOnly.booleanValue();
            INSTANCES.incrementAndGet();
            LAST_NAME.set(name);
            LAST_READ_ONLY.set(readOnly);
            LAST_STORAGE_KEY.set(storageKey);
            FILES.computeIfAbsent(name, ignored -> new byte[0]);
        }

        static void reset() {
            resetObservations();
            FILES.clear();
        }

        static void resetObservations() {
            INSTANCES.set(0);
            LAST_NAME.set(null);
            LAST_READ_ONLY.set(null);
            LAST_STORAGE_KEY.set(null);
        }

        static int instances() {
            return INSTANCES.get();
        }

        static String lastName() {
            return LAST_NAME.get();
        }

        static Boolean lastReadOnly() {
            return LAST_READ_ONLY.get();
        }

        static Object lastStorageKey() {
            return LAST_STORAGE_KEY.get();
        }

        @Override
        public long length() {
            return file().length;
        }

        @Override
        public void seek(long position) {
            this.position = position;
        }

        @Override
        public long getFilePointer() {
            return position;
        }

        @Override
        public int read() throws IOException {
            byte[] content = file();

            if (position >= content.length) {
                return -1;
            }

            return content[(int) position++] & 0xff;
        }

        @Override
        public void read(byte[] bytes, int offset, int length) throws IOException {
            byte[] content = file();

            if (position + length > content.length) {
                throw new EOFException(name);
            }

            System.arraycopy(content, (int) position, bytes, offset, length);
            position += length;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            requireWritable();
            ensureLength(position + length);

            byte[] content = file();

            System.arraycopy(bytes, offset, content, (int) position, length);
            position += length;
        }

        @Override
        public int readInt() throws IOException {
            return (readRequiredByte() << 24)
                    | (readRequiredByte() << 16)
                    | (readRequiredByte() << 8)
                    | readRequiredByte();
        }

        @Override
        public void writeInt(int value) throws IOException {
            write(new byte[] {
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value }, 0, Integer.BYTES);
        }

        @Override
        public long readLong() throws IOException {
            return ((long) readRequiredByte() << 56)
                    | ((long) readRequiredByte() << 48)
                    | ((long) readRequiredByte() << 40)
                    | ((long) readRequiredByte() << 32)
                    | ((long) readRequiredByte() << 24)
                    | ((long) readRequiredByte() << 16)
                    | ((long) readRequiredByte() << 8)
                    | readRequiredByte();
        }

        @Override
        public void writeLong(long value) throws IOException {
            write(new byte[] {
                    (byte) (value >>> 56),
                    (byte) (value >>> 48),
                    (byte) (value >>> 40),
                    (byte) (value >>> 32),
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value }, 0, Long.BYTES);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public void synch() {
        }

        @Override
        public boolean ensureLength(long newLength) {
            byte[] content = file();

            if (newLength > content.length) {
                FILES.put(name, Arrays.copyOf(content, (int) newLength));
            }

            return true;
        }

        @Override
        public boolean setLength(long newLength) {
            FILES.put(name, Arrays.copyOf(file(), (int) newLength));

            if (position > newLength) {
                position = newLength;
            }

            return true;
        }

        private int readRequiredByte() throws IOException {
            int value = read();

            if (value < 0) {
                throw new EOFException(name);
            }

            return value;
        }

        private void requireWritable() throws IOException {
            if (readOnly) {
                throw new IOException("Storage is read-only: " + name);
            }
        }

        private byte[] file() {
            return FILES.get(name);
        }
    }

    public static final class InMemoryFileAccess implements FileAccess {
        private static final Map<String, byte[]> ELEMENTS = new ConcurrentHashMap<>();

        public InMemoryFileAccess(Object storageKey) {
        }

        static void reset() {
            ELEMENTS.clear();
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
        public FileAccess.FileSync getFileSync(OutputStream outputStream) throws IOException {
            return outputStream::flush;
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

    private static final class ClassProvidingLoader extends ClassLoader {
        private final String providedClassName;
        private final Class<?> providedClass;
        private int providedLoadAttempts;

        private ClassProvidingLoader(ClassLoader parent, String providedClassName, Class<?> providedClass) {
            super(parent);
            this.providedClassName = providedClassName;
            this.providedClass = providedClass;
        }

        int providedLoadAttempts() {
            return providedLoadAttempts;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (providedClassName.equals(name)) {
                providedLoadAttempts++;

                return providedClass;
            }

            return super.loadClass(name, resolve);
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

        void reset() {
            hiddenLoadAttempts = 0;
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
