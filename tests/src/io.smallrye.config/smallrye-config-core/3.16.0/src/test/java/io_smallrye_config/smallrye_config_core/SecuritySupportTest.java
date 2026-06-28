/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class SecuritySupportTest {
    @Test
    void implicitConverterUsesDeclaredStringConstructor() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "constructor.value", "from-string-constructor"), "test-properties"))
                .build();

        ValueWithStringConstructor value = config.getValue("constructor.value", ValueWithStringConstructor.class);

        assertThat(value.value()).isEqualTo("from-string-constructor");
    }

    @Test
    void implicitConverterUsesDeclaredCharSequenceConstructor() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "constructor.value", "from-char-sequence-constructor"), "test-properties"))
                .build();

        ValueWithCharSequenceConstructor value = config.getValue("constructor.value", ValueWithCharSequenceConstructor.class);

        assertThat(value.value()).isEqualTo("from-char-sequence-constructor");
    }

    public static final class ValueWithStringConstructor {
        private final String value;

        public ValueWithStringConstructor(final String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    public static final class ValueWithCharSequenceConstructor {
        private final String value;

        public ValueWithCharSequenceConstructor(final CharSequence value) {
            this.value = value.toString();
        }

        private String value() {
            return value;
        }
    }
}
