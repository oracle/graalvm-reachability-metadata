/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.pulsar.shade.com.google.common.base.FinalizableReferenceQueue;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueTest {
    @Test
    void startsAndClosesFinalizerQueue() {
        assertThatCode(() -> {
                    try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
                        // Closing the public queue requests orderly finalizer-thread shutdown.
                    }
                })
                .doesNotThrowAnyException();
    }
}
