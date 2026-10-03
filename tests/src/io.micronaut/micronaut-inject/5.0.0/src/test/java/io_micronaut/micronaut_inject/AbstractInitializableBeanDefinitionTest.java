/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AbstractInitializableBeanDefinitionTest {
    @Test
    void injectsAPrivateFieldThroughACompiledBeanDefinition() {
        try (ApplicationContext context = ApplicationContext.run()) {
            FieldConsumer consumer = context.getBean(FieldConsumer.class);

            assertThat(consumer.message()).isEqualTo("injected value");
        }
    }

    @Singleton
    public static final class FieldConsumer {
        @Inject private FieldDependency dependency;

        public String message() {
            return dependency.message();
        }
    }

    @Singleton
    public static final class FieldDependency {
        public String message() {
            return "injected value";
        }
    }
}
