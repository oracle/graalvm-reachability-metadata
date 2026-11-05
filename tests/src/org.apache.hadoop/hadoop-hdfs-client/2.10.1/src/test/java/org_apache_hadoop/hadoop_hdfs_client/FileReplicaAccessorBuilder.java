/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.ReplicaAccessor;
import org.apache.hadoop.hdfs.ReplicaAccessorBuilder;

/// A file-backed implementation of Hadoop's replica-accessor extension API.
public class FileReplicaAccessorBuilder extends ReplicaAccessorBuilder {
    private String fileName;
    private long visibleLength;

    public FileReplicaAccessorBuilder() {
    }

    @Override
    public ReplicaAccessorBuilder setFileName(String value) {
        fileName = value;
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setVisibleLength(long value) {
        visibleLength = value;
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setBlock(long id, String pool) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setGenerationStamp(long value) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setVerifyChecksum(boolean value) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setClientName(String value) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setAllowShortCircuitReads(boolean value) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setConfiguration(Configuration value) {
        return this;
    }

    @Override
    public ReplicaAccessorBuilder setBlockAccessToken(byte[] value) {
        return this;
    }

    @Override
    public ReplicaAccessor build() {
        try {
            return new FileAccessor(new RandomAccessFile(fileName, "r"), visibleLength);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class FileAccessor extends ReplicaAccessor {
        private final RandomAccessFile file;
        private final long limit;

        private FileAccessor(RandomAccessFile file, long limit) {
            this.file = file;
            this.limit = limit;
        }

        @Override
        public int read(long position, byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position >= limit) {
                return -1;
            }
            file.seek(position);
            return file.read(buffer, offset, (int) Math.min(length, limit - position));
        }

        @Override
        public int read(long position, ByteBuffer buffer) throws IOException {
            byte[] bytes = new byte[buffer.remaining()];
            int count = read(position, bytes, 0, bytes.length);
            if (count > 0) {
                buffer.put(bytes, 0, count);
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            file.close();
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public boolean isShortCircuit() {
            return true;
        }
    }
}
