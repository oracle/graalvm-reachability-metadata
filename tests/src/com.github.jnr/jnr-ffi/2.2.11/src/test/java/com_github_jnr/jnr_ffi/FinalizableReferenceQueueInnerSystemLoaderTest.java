/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import java.lang.ref.Reference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jnr.ffi.util.ref.FinalizablePhantomReference;
import jnr.ffi.util.ref.FinalizableReferenceQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizableReferenceQueueInnerSystemLoaderTest {
    @Test
    void loadsTheFinalizerAndDispatchesPhantomReferenceCleanup() throws InterruptedException {
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        CountDownLatch cleaned = new CountDownLatch(1);
        Object referent = new Object();
        FinalizablePhantomReference<Object> reference = new FinalizablePhantomReference<Object>(referent, queue) {
            @Override
            public void finalizeReferent() {
                cleaned.countDown();
            }
        };

        assertThat(reference.enqueue()).isTrue();
        assertThat(cleaned.await(10, TimeUnit.SECONDS)).isTrue();
        Reference.reachabilityFence(referent);
        Reference.reachabilityFence(queue);
    }
}
