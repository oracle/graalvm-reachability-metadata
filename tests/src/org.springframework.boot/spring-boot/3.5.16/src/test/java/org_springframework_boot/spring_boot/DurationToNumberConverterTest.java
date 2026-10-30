/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;

import static org.assertj.core.api.Assertions.assertThat;

public class DurationToNumberConverterTest {

    @Test
    void convertsDurationToMillisecondNumbers() {
        ConversionService conversionService = new ApplicationConversionService();

        assertThat(conversionService.convert(Duration.ofSeconds(3), Long.class)).isEqualTo(3000L);
        assertThat(conversionService.convert(Duration.ofMillis(1500), Integer.class)).isEqualTo(1500);
    }

    @Test
    void sharedConversionServiceConvertsDurationToNumber() {
        ConversionService conversionService = ApplicationConversionService.getSharedInstance();

        assertThat(conversionService.convert(Duration.ofMinutes(2), Long.class)).isEqualTo(120000L);
    }
}
