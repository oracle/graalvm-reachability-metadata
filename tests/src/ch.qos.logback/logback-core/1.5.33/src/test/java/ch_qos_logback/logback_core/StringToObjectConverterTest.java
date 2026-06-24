/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.joran.util.StringToObjectConverter;
import ch.qos.logback.core.spi.ContextAwareBase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringToObjectConverterTest {

    @Test
    void convertsTypeThatFollowsPublicStaticValueOfConvention() {
        Method valueOfMethod = StringToObjectConverter.getValueOfMethod(ValueObject.class);

        assertThat(valueOfMethod).isNotNull();
        assertThat(StringToObjectConverter.canBeBuiltFromSimpleString(ValueObject.class)).isTrue();
        assertThat(StringToObjectConverter.convertArg(new ContextAwareBase(), " configured ", ValueObject.class))
                .isEqualTo(new ValueObject("configured"));
    }

    public record ValueObject(String value) {

        public static ValueObject valueOf(String value) {
            return new ValueObject(value);
        }
    }
}
