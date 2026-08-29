/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

/** Verifies convention-based object conversion methods and constructors. */
public class ObjectToObjectConverterTest {
    private final DefaultConversionService conversionService = new DefaultConversionService();

    @Test
    void convertsUsingSourceMethodStaticFactoryAndConstructor() {
        Source source = new Source("spring");

        assertThat(conversionService.convert(source, MethodTarget.class).value).isEqualTo("spring");
        assertThat(conversionService.convert(source, FactoryTarget.class).value).isEqualTo("spring");
        assertThat(conversionService.convert(source, ConstructorTarget.class).value).isEqualTo("spring");
    }

    public static final class Source {
        private final String value;

        public Source(String value) {
            this.value = value;
        }

        public MethodTarget toMethodTarget() {
            return new MethodTarget(value);
        }
    }

    public static final class MethodTarget {
        private final String value;

        public MethodTarget(String value) {
            this.value = value;
        }
    }

    public static final class FactoryTarget {
        private final String value;

        private FactoryTarget(String value) {
            this.value = value;
        }

        public static FactoryTarget of(Source source) {
            return new FactoryTarget(source.value);
        }
    }

    public static final class ConstructorTarget {
        private final String value;

        public ConstructorTarget(Source source) {
            this.value = source.value;
        }
    }
}
