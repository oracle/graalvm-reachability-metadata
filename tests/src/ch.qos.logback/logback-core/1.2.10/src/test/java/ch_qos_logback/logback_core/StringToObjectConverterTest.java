/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.joran.util.StringToObjectConverter;
import ch.qos.logback.core.spi.ContextAwareBase;
import org.junit.jupiter.api.Test;

public class StringToObjectConverterTest {

    @Test
    void recognizesTypesThatFollowTheStaticValueOfConvention() {
        assertThat(StringToObjectConverter.canBeBuiltFromSimpleString(ValueOfTarget.class)).isTrue();
    }

    @Test
    void convertsValuesUsingTheStaticValueOfConvention() {
        ContextAwareBase contextAware = new ContextAwareBase();

        Object converted = StringToObjectConverter.convertArg(contextAware, "  trimmed value  ", ValueOfTarget.class);

        assertThat(converted).isInstanceOf(ValueOfTarget.class);
        assertThat(((ValueOfTarget) converted).getValue()).isEqualTo("trimmed value");
    }

    public static final class ValueOfTarget {

        private final String value;

        private ValueOfTarget(String value) {
            this.value = value;
        }

        public static ValueOfTarget valueOf(String value) {
            return new ValueOfTarget(value);
        }

        public String getValue() {
            return value;
        }
    }
}
