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

/** Verifies convention-based object conversion through Spring's conversion service. */
public class ObjectToObjectConverterTest {
    private final DefaultConversionService conversionService = new DefaultConversionService();

    @Test
    void invokesSourceMethodStaticFactoryAndConstructor() {
        MethodSource source = new MethodSource("method");

        MethodTarget methodTarget = conversionService.convert(source, MethodTarget.class);
        FactoryTarget factoryTarget = conversionService.convert(new FactorySource("factory"), FactoryTarget.class);
        ConstructorTarget constructorTarget =
                conversionService.convert(new ConstructorSource("constructor"), ConstructorTarget.class);

        assertThat(methodTarget.value).isEqualTo("method");
        assertThat(factoryTarget.value).isEqualTo("factory");
        assertThat(constructorTarget.value).isEqualTo("constructor");
    }

    public static final class MethodSource {
        private final String value;

        public MethodSource(String value) {
            this.value = value;
        }

        public MethodTarget toMethodTarget() {
            return new MethodTarget(this.value);
        }
    }

    public static final class MethodTarget {
        private final String value;

        public MethodTarget(String value) {
            this.value = value;
        }
    }

    public static final class FactorySource {
        private final String value;

        public FactorySource(String value) {
            this.value = value;
        }
    }

    public static final class FactoryTarget {
        private final String value;

        private FactoryTarget(String value) {
            this.value = value;
        }

        public static FactoryTarget valueOf(FactorySource source) {
            return new FactoryTarget(source.value);
        }
    }

    public static final class ConstructorSource {
        private final String value;

        public ConstructorSource(String value) {
            this.value = value;
        }
    }

    public static final class ConstructorTarget {
        private final String value;

        public ConstructorTarget(ConstructorSource source) {
            this.value = source.value;
        }
    }
}
