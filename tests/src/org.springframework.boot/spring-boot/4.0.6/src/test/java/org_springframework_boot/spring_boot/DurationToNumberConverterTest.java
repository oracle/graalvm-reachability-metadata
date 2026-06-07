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

import static org.assertj.core.api.Assertions.assertThat;

public class DurationToNumberConverterTest {

    @Test
    void convertDurationToConcreteNumberTypeUsesApplicationConversionService() {
        ApplicationConversionService conversionService = new ApplicationConversionService();

        Long converted = conversionService.convert(Duration.ofSeconds(12), Long.class);

        assertThat(converted).isEqualTo(12000L);
    }

}
