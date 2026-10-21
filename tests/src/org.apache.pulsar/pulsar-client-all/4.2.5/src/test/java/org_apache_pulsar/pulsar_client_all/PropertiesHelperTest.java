/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.PropertiesHelper;
import org.junit.jupiter.api.Test;

public class PropertiesHelperTest {
    @Test
    void convertsStringThroughConstructorAndFactoryMethod() {
        ConstructorValue constructorValue = PropertiesHelper.convertValue("constructor", ConstructorValue.class);
        FactoryValue factoryValue = PropertiesHelper.convertValue("factory", FactoryValue.class);

        assertThat(constructorValue.value).isEqualTo("constructor");
        assertThat(factoryValue.value).isEqualTo("FACTORY");
    }

    public static class ConstructorValue {
        private final String value;

        public ConstructorValue(String value) {
            this.value = value;
        }
    }

    public static class FactoryValue {
        private final String value;

        private FactoryValue(String value) {
            this.value = value;
        }

        public static FactoryValue valueOf(String value) {
            return new FactoryValue(value.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
