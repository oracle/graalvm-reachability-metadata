/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import static org.assertj.core.api.Assertions.assertThat;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    @Test
    void convertsOptionArgumentUsingPublicStaticValueOfMethod() {
        OptionParser parser = new OptionParser();
        OptionSpec<ValueOfConvertedValue> option = parser.accepts("item")
                .withRequiredArg()
                .ofType(ValueOfConvertedValue.class);

        OptionSet options = parser.parse("--item", "alpha");

        assertThat(options.valueOf(option).value()).isEqualTo("valueOf:alpha");
    }

    @Test
    void convertsOptionArgumentUsingPublicStringConstructor() {
        OptionParser parser = new OptionParser();
        OptionSpec<ConstructorConvertedValue> option = parser.accepts("item")
                .withRequiredArg()
                .ofType(ConstructorConvertedValue.class);

        OptionSet options = parser.parse("--item", "bravo");

        assertThat(options.valueOf(option).value()).isEqualTo("constructor:bravo");
    }

    public static final class ValueOfConvertedValue {
        private final String value;

        private ValueOfConvertedValue(String value) {
            this.value = value;
        }

        public static ValueOfConvertedValue valueOf(String value) {
            return new ValueOfConvertedValue("valueOf:" + value);
        }

        public String value() {
            return value;
        }
    }

    public static final class ConstructorConvertedValue {
        private final String value;

        public ConstructorConvertedValue(String value) {
            this.value = "constructor:" + value;
        }

        public String value() {
            return value;
        }
    }
}
