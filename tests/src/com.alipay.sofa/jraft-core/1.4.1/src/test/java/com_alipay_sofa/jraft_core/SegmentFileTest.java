/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage.WriteContext;
import com.alipay.sofa.jraft.storage.log.SegmentFile;
import com.alipay.sofa.jraft.storage.log.SegmentFile.SegmentFileOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SegmentFileTest {
    @TempDir
    Path directory;

    @Test
    void writesCommitsAndRecoversMappedLogRecords() throws Exception {
        Path path = directory.resolve("segment");
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        SegmentFile segment = new SegmentFile(4096, path.toString(), executor);
        byte[] first = "first raft record".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second raft record".getBytes(StandardCharsets.UTF_8);
        try {
            assertTrue(segment.init(SegmentFileOptions.builder().setNewFile(true).build()));
            int firstPosition = write(segment, 11, first);
            int secondPosition = write(segment, 12, second);
            segment.sync(true);
            assertTrue(segment.contains(11));
            assertTrue(segment.contains(12));
            assertFalse(segment.contains(10));
            assertArrayEquals(first, segment.read(11, firstPosition));
            assertArrayEquals(second, segment.read(12, secondPosition));
            segment.shutdown();
            SegmentFile recovered = new SegmentFile(4096, path.toString(), executor);
            try {
                assertTrue(recovered.init(SegmentFileOptions.builder().setRecover(true).setLastFile(true).build()));
                recovered.setLastLogIndex(12);
                assertArrayEquals(first, recovered.read(11, firstPosition));
                assertArrayEquals(second, recovered.read(12, secondPosition));
                recovered.truncateSuffix(secondPosition, 11, true);
                assertFalse(recovered.contains(12));
                assertArrayEquals(first, recovered.read(11, firstPosition));
            } finally {
                recovered.shutdown();
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            segment.shutdown();
        }
        Files.delete(path);
        assertFalse(Files.exists(path));
    }

    private static int write(SegmentFile segment, long index, byte[] data) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        int position = segment.write(index, data, new WriteContext() {
            @Override
            public void finishJob() {
                completion.complete(null);
            }

            @Override
            public void setError(Exception error) {
                completion.completeExceptionally(error);
            }
        });
        completion.get(30, TimeUnit.SECONDS);
        return position;
    }
}
