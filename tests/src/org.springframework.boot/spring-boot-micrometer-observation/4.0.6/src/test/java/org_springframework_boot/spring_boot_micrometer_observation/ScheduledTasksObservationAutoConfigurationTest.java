/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_observation;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ScheduledTasksObservationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduledTasksObservationAutoConfigurationTest {
    @Test
    void installsTheApplicationRegistryIntoTheScheduledTaskRegistrar() {
        ObservationRegistry registry = ObservationRegistry.create();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObservationRegistry.class, () -> registry);
            context.register(ScheduledTasksObservationAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(SchedulingConfigurer.class).size());
            ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
            try {
                context.getBean(SchedulingConfigurer.class).configureTasks(registrar);
                assertSame(registry, registrar.getObservationRegistry());
            } finally {
                registrar.destroy();
            }
        }
    }

    @Test
    void doesNotConfigureScheduledObservationsWithoutARegistry() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ScheduledTasksObservationAutoConfiguration.class)) {
            assertTrue(context.getBeansOfType(SchedulingConfigurer.class).isEmpty());
        }
    }
}
