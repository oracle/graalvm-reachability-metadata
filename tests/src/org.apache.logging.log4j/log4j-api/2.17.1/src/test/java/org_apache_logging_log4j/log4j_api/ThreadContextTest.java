/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ThreadContextTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clearAll();
    }

    @Test
    void storesValuesInConfiguredThreadContextMap() {
        ThreadContext.put("requestId", "r-42");
        ThreadContext.put("tenant", "acme");
        ThreadContext.remove("tenant");

        assertThat(ThreadContext.get("requestId")).isEqualTo("r-42");
        assertThat(ThreadContext.containsKey("tenant")).isFalse();
        assertThat(ThreadContext.getContext()).containsExactly(entry("requestId", "r-42"));
        assertThat(RecordingThreadContextMap.OPERATIONS)
                .containsSubsequence("created", "put:requestId", "put:tenant", "remove:tenant");
    }

    @Test
    void keepsNestedDiagnosticStackSeparately() {
        ThreadContext.push("checkout");
        ThreadContext.push("payment");

        assertThat(ThreadContext.getDepth()).isEqualTo(2);
        assertThat(ThreadContext.peek()).isEqualTo("payment");
        assertThat(ThreadContext.pop()).isEqualTo("payment");
        assertThat(ThreadContext.getImmutableStack().asList()).containsExactly("checkout");
    }
}
