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
import ch.qos.logback.core.util.Duration;
import org.junit.jupiter.api.Test;

public class StringToObjectConverterTest {

    @Test
    void convertsLibraryTypeUsingStaticValueOfConvention() {
        ContextAwareBase contextAware = new ContextAwareBase();

        Object converted = StringToObjectConverter.convertArg(contextAware, "  2 seconds  ", Duration.class);

        assertThat(converted).isInstanceOf(Duration.class);
        Duration duration = (Duration) converted;
        assertThat(duration.getMilliseconds()).isEqualTo(2_000L);
    }
}
