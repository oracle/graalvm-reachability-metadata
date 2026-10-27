/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcache_1_0_spec;

import org.junit.jupiter.api.Test;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBuilderInnerClassFactoryTest {
    @Test
    void createsInstanceFromConfiguredClassName() {
        Factory<CreatedByFactory> factory = FactoryBuilder.factoryOf(CreatedByFactory.class.getName());

        CreatedByFactory instance = factory.create();

        assertThat(instance).isInstanceOf(CreatedByFactory.class);
        assertThat(instance.getValue()).isEqualTo("created");
    }

    public static class CreatedByFactory {
        private final String value;

        public CreatedByFactory() {
            this.value = "created";
        }

        String getValue() {
            return value;
        }
    }
}
