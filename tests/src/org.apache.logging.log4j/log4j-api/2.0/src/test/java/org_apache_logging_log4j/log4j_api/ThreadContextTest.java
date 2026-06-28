/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.DefaultThreadContextMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ThreadContextTest {
    private static final String THREAD_CONTEXT_MAP_PROPERTY = "log4j2.threadContextMap";

    static {
        System.setProperty(THREAD_CONTEXT_MAP_PROPERTY, DefaultThreadContextMap.class.getName());
    }

    @AfterAll
    static void clearThreadContextMapProperty() {
        System.clearProperty(THREAD_CONTEXT_MAP_PROPERTY);
        ThreadContext.clearAll();
    }

    @Test
    void keepsThreadContextUsableWithConfiguredProviderMap() {
        ThreadContext.clearAll();

        ThreadContext.put("requestId", "abc-123");
        ThreadContext.push("started");

        assertThat(ThreadContext.get("requestId")).isEqualTo("abc-123");
        assertThat(ThreadContext.containsKey("requestId")).isTrue();
        assertThat(ThreadContext.getImmutableContext()).containsEntry("requestId", "abc-123");
        assertThat(ThreadContext.peek()).isEqualTo("started");

        ThreadContext.remove("requestId");

        assertThat(ThreadContext.pop()).isEqualTo("started");
        assertThat(ThreadContext.isEmpty()).isTrue();
        assertThat(ThreadContext.getDepth()).isZero();
    }
}
