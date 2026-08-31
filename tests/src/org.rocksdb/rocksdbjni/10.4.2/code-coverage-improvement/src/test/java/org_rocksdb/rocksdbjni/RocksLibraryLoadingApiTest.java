/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RocksLibraryLoadingApiTest {

    @Test
    void concurrentPublicLibraryLoadsCompleteSuccessfully() throws Exception {
        int threadCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> loads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                loads.add(executor.submit(() -> {
                    start.await();
                    RocksDB.loadLibrary();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> load : loads) {
                load.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
