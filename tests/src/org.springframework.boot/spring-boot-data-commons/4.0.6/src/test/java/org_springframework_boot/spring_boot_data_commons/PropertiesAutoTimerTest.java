/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties;
import org.springframework.boot.data.autoconfigure.metrics.PropertiesAutoTimer;
import org.springframework.boot.data.metrics.AutoTimer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesAutoTimerTest {

    @Test
    void bindsMetricsSettingsAndPublishesConfiguredPercentiles() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "management.metrics.data.repository.metric-name", "books.operations",
                "management.metrics.data.repository.autotime.enabled", "true",
                "management.metrics.data.repository.autotime.percentiles-histogram", "true",
                "management.metrics.data.repository.autotime.percentiles", "0.5,0.95"));
        DataMetricsProperties properties = new Binder(source).bind("management.metrics.data", DataMetricsProperties.class).get();
        PropertiesAutoTimer autoTimer = new PropertiesAutoTimer(properties.getRepository().getAutotime());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Timer timer = autoTimer.builder(properties.getRepository().getMetricName()).register(registry);
            timer.record(Duration.ofMillis(20));
            timer.record(Duration.ofMillis(40));

            assertTrue(autoTimer.isEnabled());
            assertTrue(properties.getRepository().getAutotime().isPercentilesHistogram());
            assertArrayEquals(new double[] {0.5, 0.95}, properties.getRepository().getAutotime().getPercentiles());
            assertEquals("books.operations", timer.getId().getName());
            assertEquals(2, timer.count());
            assertEquals(60.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
            ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
            assertEquals(2, percentiles.length);
            assertEquals(0.5, percentiles[0].percentile());
            assertEquals(0.95, percentiles[1].percentile());
            assertTrue(percentiles[0].value(TimeUnit.MILLISECONDS) > 0);
            assertTrue(percentiles[1].value(TimeUnit.MILLISECONDS) >= percentiles[0].value(TimeUnit.MILLISECONDS));
        } finally {
            registry.close();
        }
    }

    @Test
    void disabledPropertyPreventsAutomaticRecording() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "management.metrics.data.repository.autotime.enabled", "false"));
        DataMetricsProperties properties = new Binder(source).bind("management.metrics.data", DataMetricsProperties.class).get();
        PropertiesAutoTimer autoTimer = new PropertiesAutoTimer(properties.getRepository().getAutotime());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AutoTimer.apply(autoTimer, properties.getRepository().getMetricName(), Set.of(),
                    builder -> builder.register(registry).record(Duration.ofMillis(10)));

            assertFalse(autoTimer.isEnabled());
            assertTrue(registry.getMeters().isEmpty());
        } finally {
            registry.close();
        }
    }
}
