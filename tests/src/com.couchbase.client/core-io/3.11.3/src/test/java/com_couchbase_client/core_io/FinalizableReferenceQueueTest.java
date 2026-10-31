/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.base.FinalizableReferenceQueue;
import com.couchbase.client.core.deps.com.google.common.base.FinalizableWeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FinalizableReferenceQueueTest {
    @Test
    void exercisesPublicApi() throws Exception {
        CountDownLatch cleaned = new CountDownLatch(1);
        Object value = new Object();
        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            Cleanup reference = new Cleanup(value, queue, cleaned);
            reference.clear();
            assertTrue(reference.enqueue());
            assertTrue(cleaned.await(10, TimeUnit.SECONDS));
        }
    }

    public static class Cleanup extends FinalizableWeakReference<Object> {
        private final CountDownLatch cleaned;

        public Cleanup(Object value, FinalizableReferenceQueue queue, CountDownLatch cleaned) {
            super(value, queue);
            this.cleaned = cleaned;
        }

        @Override
        public void finalizeReferent() {
            cleaned.countDown();
        }
    }
}
