/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.functors.InstantiateFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiateFactoryTest {

    @Test
    void createsInstancesThroughPublicParameterizedConstructor() {
        Factory<ConstructedValue> factory = InstantiateFactory.instantiateFactory(
                ConstructedValue.class,
                new Class<?>[]{String.class, Integer.class},
                new Object[]{"alpha", 7}
        );

        ConstructedValue created = factory.create();

        assertThat(created.describe()).isEqualTo("alpha-7");
    }

    public static final class ConstructedValue {

        private final String prefix;
        private final Integer number;

        public ConstructedValue(String prefix, Integer number) {
            this.prefix = prefix;
            this.number = number;
        }

        public String describe() {
            return prefix + "-" + number;
        }
    }
}
