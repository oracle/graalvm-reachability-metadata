/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.FinalizableReferenceQueue;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizableReferenceQueueTest {
    @Test
    void startsAndClosesItsFinalizerWorker() {
        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            assertThat(queue).isNotNull();
        }
    }
}
