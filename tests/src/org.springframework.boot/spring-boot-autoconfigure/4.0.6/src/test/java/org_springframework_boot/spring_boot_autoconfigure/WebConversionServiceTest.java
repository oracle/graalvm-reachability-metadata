/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters;
import org.springframework.boot.autoconfigure.web.format.WebConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebConversionServiceTest {
    @Test
    void injectedWebConversionServiceParsesAndFormatsConfiguredDateAndTimePatterns() {
        new ApplicationContextRunner().withUserConfiguration(WebFormatting.class)
                .withBean(DateTimeFormatters.class, () -> new DateTimeFormatters()
                        .dateFormat("dd/MM/yyyy").timeFormat("HH:mm").dateTimeFormat("yyyy-MM-dd HH:mm"))
                .run(context -> {
                    ConversionService conversion = context.getBean(WebConversionService.class);
                    assertEquals(LocalDate.of(2026, 5, 12), conversion.convert("12/05/2026", LocalDate.class));
                    assertEquals("12/05/2026", conversion.convert(LocalDate.of(2026, 5, 12), String.class));
                    assertEquals(LocalTime.of(14, 35), conversion.convert("14:35", LocalTime.class));
                    assertEquals(LocalDateTime.of(2026, 5, 12, 14, 35),
                            conversion.convert("2026-05-12 14:35", LocalDateTime.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(WebConversionService.class)
    public static class WebFormatting {
    }
}
