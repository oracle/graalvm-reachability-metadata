/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.BufferPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs zstd-jni's finalizer-free `ZstdOutputStreamNoFinalizer` and `ZstdInputStreamNoFinalizer`
 * on an application-supplied `BufferPool`: a continuous-mode reader tails a frame that is still
 * being written, per-segment frames share one connection through
 * `closeWithoutClosingParentStream`, and a pool that refuses a buffer caps how many streams
 * may be open at once.
 *
 * A log-shipping agent on each host compresses what services log into `ingest.log.zst` and,
 * in the same process, tails that file to forward every flushed batch before the frame is
 * finished. It ships segments to a collector as separate frames over one long-lived
 * connection, which the collector decodes with Commons Compress. All stream buffers come from
 * the agent's own budgeted pool, so buffers are reused across streams and a burst of streams
 * beyond the memory budget is rejected instead of allocating.
 */
class PooledLogTailShipperTest {

    private static final int LEVEL = 6;

    private static final List<String> BATCHES = List.of(
            """
            2026-10-19T08:00:00Z INFO  ingest started shard=7
            2026-10-19T08:00:01Z INFO  partition=0 offset=1200 lag=0
            """,
            """
            2026-10-19T08:00:05Z WARN  partition=3 offset=88 lag=1400 slow consumer
            2026-10-19T08:00:06Z INFO  partition=3 rebalanced owner=agent-2
            2026-10-19T08:00:07Z INFO  partition=0 offset=1260 lag=0
            """,
            """
            2026-10-19T08:00:12Z ERROR partition=5 checksum mismatch, refetching segment 41
            """,
            """
            2026-10-19T08:00:15Z INFO  partition=5 segment 41 refetched in 212ms
            2026-10-19T08:00:16Z INFO  ingest checkpoint written
            """);

    @TempDir
    Path directory;

    @Test
    void continuousReaderForwardsEveryFlushedBatchOfAnUnfinishedFrame() throws IOException {
        Path log = directory.resolve("ingest.log.zst");
        BudgetedBufferPool pool = new BudgetedBufferPool(2);
        StringBuilder forwarded = new StringBuilder();

        ZstdOutputStreamNoFinalizer writer = new ZstdOutputStreamNoFinalizer(Files.newOutputStream(log), pool, LEVEL);
        writer.setChecksum(true);
        try (ZstdInputStreamNoFinalizer tailer =
                new ZstdInputStreamNoFinalizer(new FileInputStream(log.toFile()), pool).setContinuous(true)) {
            assertThat(tailer.getContinuous()).isTrue();
            assertThat(pool.outstanding()).isEqualTo(2);

            for (String batch : BATCHES) {
                writer.write(batch.getBytes(StandardCharsets.UTF_8));
                writer.flush();

                String received = drain(tailer);
                assertThat(received).isEqualTo(batch);
                forwarded.append(received);
                // Caught up with the writer: the open frame ends early, which is not an error here.
                assertThat(tailer.read()).isEqualTo(-1);
            }

            writer.close();
            assertThat(drain(tailer)).isEmpty();
        } finally {
            writer.close();
        }

        assertThat(forwarded.toString()).isEqualTo(String.join("", BATCHES));
        assertThat(pool.outstanding()).isZero();
        try (ZstdInputStreamNoFinalizer reader = new ZstdInputStreamNoFinalizer(Files.newInputStream(log), pool)) {
            assertThat(new String(reader.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(forwarded.toString());
        }
    }

    @Test
    void segmentsShareOneConnectionAndReuseThePooledBuffers() throws IOException {
        BudgetedBufferPool pool = new BudgetedBufferPool(2);
        CollectorConnection connection = new CollectorConnection();
        StringBuilder sent = new StringBuilder();

        for (int segment = 0; segment < 8; segment++) {
            String payload = BATCHES.get(segment % BATCHES.size()).replace("shard=7", "shard=" + segment);
            ZstdOutputStreamNoFinalizer frame = new ZstdOutputStreamNoFinalizer(connection, pool);
            frame.setLevel(3).setChecksum(true);
            frame.write(payload.getBytes(StandardCharsets.UTF_8));
            frame.closeWithoutClosingParentStream();
            sent.append(payload);
        }

        assertThat(connection.closed).isFalse();
        assertThat(pool.outstanding()).isZero();
        assertThat(pool.borrows()).isEqualTo(8);
        assertThat(pool.allocations()).isEqualTo(1);

        byte[] wire = connection.toByteArray();
        assertThat(ZstdUtils.matches(wire, wire.length)).isTrue();

        try (ZstdCompressorInputStream collector = new ZstdCompressorInputStream(new ByteArrayInputStream(wire), pool)) {
            assertThat(new String(collector.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(sent.toString());
            assertThat(collector.getCompressedCount()).isEqualTo(wire.length);
        }
        assertThat(pool.outstanding()).isZero();
        assertThat(pool.allocations()).isEqualTo(2);
    }

    @Test
    void poolBudgetRejectsStreamsBeyondItUntilOneIsClosed() throws IOException {
        BudgetedBufferPool pool = new BudgetedBufferPool(1);
        ByteArrayOutputStream spool = new ByteArrayOutputStream();
        byte[] batch = BATCHES.get(1).getBytes(StandardCharsets.UTF_8);

        ZstdOutputStreamNoFinalizer open = new ZstdOutputStreamNoFinalizer(spool, pool, LEVEL);
        assertThatThrownBy(() -> new ZstdInputStreamNoFinalizer(new ByteArrayInputStream(new byte[0]), pool))
                .isInstanceOfSatisfying(ZstdIOException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(Zstd.errMemoryAllocation());
                    assertThat(e).hasMessageContaining("from the BufferPool");
                });
        assertThat(pool.refusals()).isEqualTo(1);

        open.write(batch);
        open.close();
        assertThat(pool.outstanding()).isZero();

        try (ZstdInputStreamNoFinalizer reader =
                new ZstdInputStreamNoFinalizer(new ByteArrayInputStream(spool.toByteArray()), pool)) {
            assertThat(reader.readAllBytes()).isEqualTo(batch);
        }
        assertThat(pool.outstanding()).isZero();
        assertThat(pool.refusals()).isEqualTo(1);
    }

    /** Reads what the tailer can decode now; continuous mode reports `-1` once it has caught up. */
    private static String drain(ZstdInputStreamNoFinalizer tailer) throws IOException {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        byte[] chunk = new byte[64];
        for (int attempt = 0; attempt < 10_000; attempt++) {
            int read = tailer.read(chunk, 0, chunk.length);
            if (read < 0) {
                return decoded.toString(StandardCharsets.UTF_8);
            }
            decoded.write(chunk, 0, read);
        }
        throw new AssertionError("tailer never caught up with the writer");
    }

    /** Heap buffers recycled per capacity, with at most `budget` of them lent out at once. */
    static final class BudgetedBufferPool implements BufferPool {

        private final int budget;
        private final Map<Integer, Deque<ByteBuffer>> idle = new HashMap<>();
        private int outstanding;
        private int allocations;
        private int borrows;
        private int refusals;

        BudgetedBufferPool(int budget) {
            this.budget = budget;
        }

        @Override
        public synchronized ByteBuffer get(int capacity) {
            if (outstanding == budget) {
                refusals++;
                return null;
            }
            ByteBuffer buffer = idle.computeIfAbsent(capacity, c -> new ArrayDeque<>()).pollFirst();
            if (buffer == null) {
                buffer = ByteBuffer.allocate(capacity);
                allocations++;
            }
            outstanding++;
            borrows++;
            return buffer;
        }

        @Override
        public synchronized void release(ByteBuffer buffer) {
            outstanding--;
            buffer.clear();
            idle.computeIfAbsent(buffer.capacity(), c -> new ArrayDeque<>()).addFirst(buffer);
        }

        synchronized int outstanding() {
            return outstanding;
        }

        synchronized int allocations() {
            return allocations;
        }

        synchronized int borrows() {
            return borrows;
        }

        synchronized int refusals() {
            return refusals;
        }
    }

    /** The collector connection: frames are appended to it and only the agent may close it. */
    static final class CollectorConnection extends ByteArrayOutputStream {

        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
