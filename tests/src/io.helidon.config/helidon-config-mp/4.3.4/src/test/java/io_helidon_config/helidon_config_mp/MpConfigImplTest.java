/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MpConfigImplTest {
    @Test
    void convertsValuesWithImplicitStaticMethodAndStringConstructor() {
        Config config = config(Map.of(
                "method.values", "alpha,beta",
                "constructor.values.0", "gamma",
                "constructor.values.1", "delta"));

        MethodBasedValue[] methodValues = config.getValue("method.values", MethodBasedValue[].class);
        Optional<ConstructorBasedValue[]> constructorValues = config.getOptionalValue(
                "constructor.values",
                ConstructorBasedValue[].class);

        assertArrayEquals(new MethodBasedValue[] {new MethodBasedValue("method:alpha"),
                new MethodBasedValue("method:beta")}, methodValues);
        assertTrue(constructorValues.isPresent());
        assertArrayEquals(new ConstructorBasedValue[] {new ConstructorBasedValue("gamma"),
                new ConstructorBasedValue("delta")}, constructorValues.get());
    }

    @Test
    void exposesArrayConverterForImplicitComponentConverters() {
        Config config = config(Map.of());

        Converter<MethodBasedValue[]> converter = config.getConverter(MethodBasedValue[].class).orElseThrow();
        MethodBasedValue[] converted = converter.convert("first,second");

        assertArrayEquals(new MethodBasedValue[] {new MethodBasedValue("method:first"),
                new MethodBasedValue("method:second")}, converted);
    }

    private static Config config(Map<String, String> values) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create("test-values", new LinkedHashMap<>(values)))
                .build();
    }

    public static final class MethodBasedValue {
        private final String value;

        private MethodBasedValue(String value) {
            this.value = value;
        }

        public static MethodBasedValue of(String value) {
            return new MethodBasedValue("method:" + value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MethodBasedValue that)) {
                return false;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static final class ConstructorBasedValue {
        private final String value;

        public ConstructorBasedValue(String value) {
            this.value = "constructor:" + value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstructorBasedValue that)) {
                return false;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
