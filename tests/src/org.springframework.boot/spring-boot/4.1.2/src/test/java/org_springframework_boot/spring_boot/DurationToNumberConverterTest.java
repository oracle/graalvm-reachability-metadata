/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import static org.assertj.core.api.Assertions.assertThat;

public class DurationToNumberConverterTest {
    @Test
    void convertsDurationToNumericMilliseconds() {
        ApplicationConversionService service = new ApplicationConversionService();
        Duration duration = Duration.ofMillis(2345);

        assertThat(service.convert(duration, Long.class)).isEqualTo(2345L);
        assertThat(service.convert(duration, Integer.class)).isEqualTo(2345);
        assertThat(service.convert(duration, BigInteger.class)).isEqualTo(BigInteger.valueOf(2345));
        assertThat(service.convert(duration, BigDecimal.class)).isEqualByComparingTo("2345");
    }
}
