/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.curator.shaded.com.google.common.base.FinalizableReferenceQueue;
import org.apache.curator.shaded.com.google.common.base.FinalizableWeakReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizerTest {
    @Test
    void cleansAnEnqueuedReferenceWhenTheQueueCloses() {
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        AtomicBoolean cleaned = new AtomicBoolean();
        try {
            FinalizableWeakReference<Object> reference = new FinalizableWeakReference<>(new Object(), queue) {
                @Override
                public void finalizeReferent() {
                    cleaned.set(true);
                }
            };
            reference.enqueue();
            queue.close();
            assertThat(cleaned).hasValue(true);
        } finally {
            queue.close();
        }
    }
}
