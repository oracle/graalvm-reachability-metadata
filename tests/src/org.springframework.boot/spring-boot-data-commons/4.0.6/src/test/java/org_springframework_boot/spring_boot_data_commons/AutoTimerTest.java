/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.time.Duration;
import java.util.Set;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.data.metrics.AutoTimer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoTimerTest {

    @Test
    void customizesBothBuilderFactories() {
        AutoTimer autoTimer = builder -> builder.tags("store", "books").description("Book operations");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Timer named = autoTimer.builder("repository.named").register(registry);
            Timer supplied = autoTimer.builder(() -> Timer.builder("repository.supplied")).register(registry);
            named.record(Duration.ofMillis(12));
            supplied.record(Duration.ofMillis(8));

            assertTrue(autoTimer.isEnabled());
            assertEquals("books", named.getId().getTag("store"));
            assertEquals("books", supplied.getId().getTag("store"));
            assertEquals("Book operations", named.getId().getDescription());
            assertEquals(1, named.count());
            assertEquals(1, supplied.count());
        } finally {
            registry.close();
        }
    }

    @Test
    void enabledAutoTimerRecordsWithoutAnnotations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AutoTimer.apply(AutoTimer.ENABLED, "repository.calls", Set.of(),
                    builder -> builder.register(registry).record(Duration.ofMillis(5)));

            assertTrue(AutoTimer.ENABLED.isEnabled());
            assertEquals(1, registry.get("repository.calls").timer().count());
        } finally {
            registry.close();
        }
    }

    @Test
    void disabledOrAbsentAutoTimerCreatesNoMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AutoTimer.apply(AutoTimer.DISABLED, "repository.disabled", Set.of(), builder -> builder.register(registry));
            AutoTimer.apply(null, "repository.absent", Set.of(), builder -> builder.register(registry));

            assertFalse(AutoTimer.DISABLED.isEnabled());
            assertTrue(registry.getMeters().isEmpty());
        } finally {
            registry.close();
        }
    }
}
