/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileUtil;
import org.hsqldb.lib.Storage;

public final class FileAccessTestSupport {
    private FileAccessTestSupport() {
    }

    public static class DiskAccess implements FileAccess {
        static volatile Object lastKey;
        private final FileAccess delegate = FileUtil.getFileAccess(false);

        public DiskAccess(Object key) {
            lastKey = key;
        }

        @Override
        public InputStream openInputStreamElement(String name) throws IOException {
            return delegate.openInputStreamElement(name);
        }

        @Override
        public OutputStream openOutputStreamElement(String name, boolean append) throws IOException {
            return delegate.openOutputStreamElement(name, append);
        }

        @Override
        public boolean isStreamElement(String name) {
            return delegate.isStreamElement(name);
        }

        @Override
        public void createParentDirs(String name) {
            delegate.createParentDirs(name);
        }

        @Override
        public void removeElement(String name) {
            delegate.removeElement(name);
        }

        @Override
        public void renameElement(String source, String target, boolean copyIfFailed) {
            delegate.renameElement(source, target, copyIfFailed);
        }

        @Override
        public FileSync getFileSync(OutputStream stream) throws IOException {
            return delegate.getFileSync(stream);
        }
    }

    public static class DiskStorage implements Storage {
        static final AtomicInteger opens = new AtomicInteger();
        private final RandomAccessFile file;
        private final boolean readOnly;

        public DiskStorage(String name, Boolean readOnly, Object key) throws IOException {
            file = new RandomAccessFile(name, readOnly ? "r" : "rw");
            this.readOnly = readOnly;
            opens.incrementAndGet();
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
        public void read(byte[] bytes, int offset, int length) throws IOException {
            file.readFully(bytes, offset, length);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            file.write(bytes, offset, length);
        }

        @Override
        public int readInt() throws IOException {
            return file.readInt();
        }

        @Override
        public void writeInt(int value) throws IOException {
            file.writeInt(value);
        }

        @Override
        public long readLong() throws IOException {
            return file.readLong();
        }

        @Override
        public void writeLong(long value) throws IOException {
            file.writeLong(value);
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

    static String configuration(String key) {
        return ";fileaccess_class_name=org_hsqldb.hsqldb.FileAccessTestSupport$DiskAccess"
                + ";storage_class_name=org_hsqldb.hsqldb.FileAccessTestSupport$DiskStorage;storage_key=" + key;
    }
}
