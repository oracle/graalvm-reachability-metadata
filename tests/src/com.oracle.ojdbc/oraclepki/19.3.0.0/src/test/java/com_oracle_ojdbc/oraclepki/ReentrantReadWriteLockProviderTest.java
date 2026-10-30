/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import oracle.security.pki.ReentrantReadWriteLockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class ReentrantReadWriteLockProviderTest {
    @TempDir Path directory;

    @Test
    @Timeout(60)
    void writerProceedsAfterReaderReleasesPath() throws Exception {
        String path = directory.resolve("wallet").toString();
        AtomicInteger value = new AtomicInteger();
        CountDownLatch attemptingWrite = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> writer;
            ReentrantReadWriteLockProvider.lockForRead(path);
            try {
                writer = executor.submit(() -> {
                    attemptingWrite.countDown();
                    ReentrantReadWriteLockProvider.lockForWrite(path);
                    try {
                        return value.incrementAndGet();
                    } finally {
                        ReentrantReadWriteLockProvider.unlockForWrite(path);
                    }
                });
                assertThat(attemptingWrite.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(value.get()).isZero();
            } finally {
                ReentrantReadWriteLockProvider.unlockForRead(path);
            }
            assertThat(writer.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(value.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
