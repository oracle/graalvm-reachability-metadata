/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.DefaultLifecycleProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LifecycleAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LifecycleAutoConfiguration.class));

    @Test
    void bindsShutdownTimeoutAndManagesApplicationLifecycle() {
        ManagedService service = new ManagedService();
        runner.withPropertyValues("spring.lifecycle.timeout-per-shutdown-phase=45s")
                .withBean("managedService", ManagedService.class, () -> service)
                .run(context -> {
                    LifecycleProperties properties = context.getBean(LifecycleProperties.class);
                    assertEquals(Duration.ofSeconds(45), properties.getTimeoutPerShutdownPhase());
                    DefaultLifecycleProcessor processor = context.getBean(
                            "lifecycleProcessor", DefaultLifecycleProcessor.class);
                    assertTrue(processor.isRunning());
                    assertTrue(service.isRunning());
                    assertEquals(1, service.starts);

                    context.stop();
                    assertFalse(processor.isRunning());
                    assertFalse(service.isRunning());
                    assertEquals(1, service.stops);

                    context.start();
                    assertTrue(service.isRunning());
                    assertEquals(2, service.starts);
                });
        assertFalse(service.isRunning());
        assertEquals(2, service.stops);
    }

    @Test
    void preservesAnApplicationProvidedLifecycleProcessor() {
        DefaultLifecycleProcessor customProcessor = new DefaultLifecycleProcessor();
        runner.withBean("lifecycleProcessor", DefaultLifecycleProcessor.class, () -> customProcessor)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(DefaultLifecycleProcessor.class).size());
                    assertSame(customProcessor, context.getBean("lifecycleProcessor"));
                    assertTrue(customProcessor.isRunning());
                });
        assertFalse(customProcessor.isRunning());
    }

    public static class ManagedService implements SmartLifecycle {
        private boolean running;
        private int starts;
        private int stops;

        @Override
        public void start() {
            running = true;
            starts++;
        }

        @Override
        public void stop() {
            running = false;
            stops++;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
