/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.ThreadContextMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ThreadContextTest {
    private static final String THREAD_CONTEXT_MAP_PROPERTY = "log4j2.threadContextMap";

    static {
        System.setProperty(THREAD_CONTEXT_MAP_PROPERTY, ProviderThreadContextMap.class.getName());
    }

    @AfterAll
    static void clearThreadContextMapProperty() {
        System.clearProperty(THREAD_CONTEXT_MAP_PROPERTY);
        ThreadContext.clearAll();
    }

    @Test
    void usesConfiguredThreadContextMap() {
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

    public static class ProviderThreadContextMap implements ThreadContextMap {
        private final ThreadLocal<Map<String, String>> context = new ThreadLocal<Map<String, String>>();

        public ProviderThreadContextMap() {
        }

        @Override
        public void put(final String key, final String value) {
            final Map<String, String> map = getMutableContext();
            map.put(key, value);
            context.set(Collections.unmodifiableMap(map));
        }

        @Override
        public String get(final String key) {
            final Map<String, String> map = context.get();
            return map == null ? null : map.get(key);
        }

        @Override
        public void remove(final String key) {
            final Map<String, String> map = context.get();
            if (map != null) {
                final Map<String, String> copy = new HashMap<String, String>(map);
                copy.remove(key);
                context.set(Collections.unmodifiableMap(copy));
            }
        }

        @Override
        public void clear() {
            context.remove();
        }

        @Override
        public boolean containsKey(final String key) {
            final Map<String, String> map = context.get();
            return map != null && map.containsKey(key);
        }

        @Override
        public Map<String, String> getCopy() {
            return getMutableContext();
        }

        @Override
        public Map<String, String> getImmutableMapOrNull() {
            return context.get();
        }

        @Override
        public boolean isEmpty() {
            final Map<String, String> map = context.get();
            return map == null || map.isEmpty();
        }

        private Map<String, String> getMutableContext() {
            final Map<String, String> map = context.get();
            return map == null ? new HashMap<String, String>() : new HashMap<String, String>(map);
        }
    }
}
