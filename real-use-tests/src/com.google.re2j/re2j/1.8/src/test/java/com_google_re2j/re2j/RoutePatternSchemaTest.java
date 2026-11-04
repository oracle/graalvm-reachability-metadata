/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the pattern-schema introspection subsystem to validate configurable message routes.
 */
public class RoutePatternSchemaTest {

    @Test
    @Timeout(60)
    void validatesCaptureSchemaBeforeDispatchingMessages() {
        RouteCompiler compiler = new RouteCompiler();
        CompiledRoute route = compiler.compile(
                "tenant=(?<tenant>[a-z-]+);event=(?<event>[a-z.]+)");

        RouteDecision decision = route.dispatch("tenant=payments-prod;event=invoice.created");

        assertEquals(Map.of("tenant", 1, "event", 2), route.captureSchema());
        assertEquals(new RouteDecision("payments-prod", "invoice.created"), decision);
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile("tenant=(?<tenant>[a-z-]+);event=[a-z.]+"));
    }

    /** Application startup validation and dispatch for user-configured message routes. */
    private static final class RouteCompiler {

        private static final Set<String> REQUIRED_CAPTURES = Set.of("tenant", "event");

        CompiledRoute compile(String expression) {
            Pattern pattern = Pattern.compile(expression);
            Map<String, Integer> captureSchema = pattern.namedGroups();
            if (!captureSchema.keySet().containsAll(REQUIRED_CAPTURES)) {
                throw new IllegalArgumentException("Route must capture tenant and event");
            }
            return new CompiledRoute(pattern, captureSchema);
        }
    }

    /** A compiled application route that uses its validated capture schema for dispatch. */
    private static final class CompiledRoute {

        private final Pattern pattern;
        private final Map<String, Integer> captureSchema;

        CompiledRoute(Pattern pattern, Map<String, Integer> captureSchema) {
            this.pattern = pattern;
            this.captureSchema = captureSchema;
        }

        Map<String, Integer> captureSchema() {
            return captureSchema;
        }

        RouteDecision dispatch(String message) {
            Matcher matcher = pattern.matcher(message);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Message does not match route");
            }
            return new RouteDecision(
                    matcher.group(captureSchema.get("tenant")),
                    matcher.group(captureSchema.get("event")));
        }
    }

    /** The routing facts an application sends to its tenant-specific consumer. */
    private record RouteDecision(String tenant, String event) {
    }
}
