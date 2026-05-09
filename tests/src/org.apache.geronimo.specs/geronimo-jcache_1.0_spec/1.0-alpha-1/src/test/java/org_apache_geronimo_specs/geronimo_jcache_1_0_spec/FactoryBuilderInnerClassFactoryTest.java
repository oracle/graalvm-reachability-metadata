/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcache_1_0_spec;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBuilderInnerClassFactoryTest {

    @Test
    void createsNewInstanceUsingContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
        try {
            Factory<CreatableValue> factory = FactoryBuilder.factoryOf(CreatableValue.class);

            CreatableValue firstValue = factory.create();
            CreatableValue secondValue = factory.create();

            assertThat(firstValue).isInstanceOf(CreatableValue.class);
            assertThat(secondValue).isInstanceOf(CreatableValue.class);
            assertThat(secondValue).isNotSameAs(firstValue);
            assertThat(firstValue.description()).isEqualTo("created-by-class-factory");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class CreatableValue {

        public CreatableValue() {
        }

        String description() {
            return "created-by-class-factory";
        }
    }
}
