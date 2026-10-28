/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javax.annotation.PostConstruct;
import org.apache.pulsar.shade.javax.annotation.PreDestroy;
import org.apache.pulsar.shade.org.glassfish.jersey.client.innate.inject.NonInjectionManager;
import org.junit.jupiter.api.Test;

public class NonInjectionManagerTest {
    @Test
    void createsAndRunsLifecycleMethodsOnClientComponents() {
        NonInjectionManager manager = new NonInjectionManager();

        LifecycleComponent component = manager.justCreate(LifecycleComponent.class);
        assertThat(component.state()).isEqualTo("initialized");

        manager.preDestroy(component);
        assertThat(component.state()).isEqualTo("destroyed");
    }

    public static class LifecycleComponent {
        private String state = "created";

        public LifecycleComponent() {
        }

        @PostConstruct
        public void initialize() {
            state = "initialized";
        }

        @PreDestroy
        public void destroy() {
            state = "destroyed";
        }

        public String state() {
            return state;
        }
    }
}
