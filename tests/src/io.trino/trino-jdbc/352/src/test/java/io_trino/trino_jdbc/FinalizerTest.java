/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FinalizerTest {
    @Test
    void dispatchesReferenceCleanupCallback() throws Exception {
        assertTrue(ReferenceCleanup.awaitCallbacks(1));
    }
}
