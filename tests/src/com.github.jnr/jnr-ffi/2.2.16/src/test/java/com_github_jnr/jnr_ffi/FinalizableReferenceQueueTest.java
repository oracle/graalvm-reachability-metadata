/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.util.ref.FinalizableReferenceQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizableReferenceQueueTest {
    @Test
    void startsAndRegistersAReferenceQueue() {
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();

        assertThat(queue).isNotNull();
        FinalizableReferenceQueue.cleanUpAll();
    }
}
