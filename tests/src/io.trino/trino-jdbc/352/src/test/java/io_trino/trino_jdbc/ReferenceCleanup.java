/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.FinalizableReferenceQueue;
import io.trino.jdbc.$internal.guava.base.FinalizableWeakReference;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class ReferenceCleanup {
    private ReferenceCleanup() {
    }

    static boolean awaitCallbacks(int count) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(count);
        List<FinalizableWeakReference<Object>> references = new ArrayList<>();
        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            for (int i = 0; i < count; i++) {
                FinalizableWeakReference<Object> reference = new FinalizableWeakReference<>(new Object(), queue) {
                    @Override
                    public void finalizeReferent() {
                        completed.countDown();
                    }
                };
                references.add(reference);
                reference.enqueue();
            }
            boolean result = completed.await(10, TimeUnit.SECONDS);
            Reference.reachabilityFence(references);
            return result;
        }
    }
}
