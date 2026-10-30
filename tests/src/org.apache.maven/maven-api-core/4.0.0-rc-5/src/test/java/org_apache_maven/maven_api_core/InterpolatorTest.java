/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.apache.maven.api.services.Interpolator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InterpolatorTest {
    @Test
    void resolvesPropertiesInPriorityOrder() {
        Map<String, String> user = Map.of("name", "user-application");
        Map<String, String> system = Map.of("name", "system-application", "directory", "workspace");
        AtomicInteger fallbackCalls = new AtomicInteger();
        UnaryOperator<String> fallback = key -> {
            fallbackCalls.incrementAndGet();
            return "fallback".equals(key) ? "default-value" : null;
        };
        UnaryOperator<String> resolver = Interpolator.chain(user::get, system::get, fallback);

        assertEquals("user-application", resolver.apply("name"));
        assertEquals("workspace", resolver.apply("directory"));
        assertEquals(0, fallbackCalls.get());
        assertEquals("default-value", resolver.apply("fallback"));
        assertNull(resolver.apply("unknown"));
        assertEquals(2, fallbackCalls.get());
        assertEquals("workspace", Interpolator.chain(List.<UnaryOperator<String>>of(user::get, system::get))
                .apply("directory"));
        assertNull(Interpolator.chain(List.of()).apply("name"));
    }

    @Test
    void memoizesResolvedAndUnresolvedValuesSeparately() {
        AtomicInteger calls = new AtomicInteger();
        UnaryOperator<String> resolver = Interpolator.memoize(key -> {
            calls.incrementAndGet();
            return "name".equals(key) ? "application" : null;
        });

        assertEquals("application", resolver.apply("name"));
        assertEquals("application", resolver.apply("name"));
        assertEquals(1, calls.get());
        assertNull(resolver.apply("missing"));
        assertNull(resolver.apply("missing"));
        assertEquals(2, calls.get());
    }
}
