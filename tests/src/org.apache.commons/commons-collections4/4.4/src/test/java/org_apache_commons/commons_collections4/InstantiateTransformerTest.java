/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.functors.InstantiateTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiateTransformerTest {

    @Test
    void createsInstanceThroughPublicNoArgConstructor() {
        Transformer<Class<? extends NoArgConstructedValue>, NoArgConstructedValue> transformer =
                InstantiateTransformer.instantiateTransformer();

        NoArgConstructedValue created = transformer.transform(NoArgConstructedValue.class);

        assertThat(created.describe()).isEqualTo("created-with-default-constructor");
    }

    @Test
    void createsInstanceThroughPublicParameterizedConstructor() {
        Transformer<Class<? extends ParameterizedConstructedValue>, ParameterizedConstructedValue> transformer =
                InstantiateTransformer.instantiateTransformer(
                        new Class<?>[]{String.class, Integer.class},
                        new Object[]{"alpha", 7}
                );

        ParameterizedConstructedValue created = transformer.transform(ParameterizedConstructedValue.class);

        assertThat(created.describe()).isEqualTo("alpha-7");
    }

    public static final class NoArgConstructedValue {

        public String describe() {
            return "created-with-default-constructor";
        }
    }

    public static final class ParameterizedConstructedValue {

        private final String prefix;
        private final Integer number;

        public ParameterizedConstructedValue(String prefix, Integer number) {
            this.prefix = prefix;
            this.number = number;
        }

        public String describe() {
            return prefix + "-" + number;
        }
    }
}
