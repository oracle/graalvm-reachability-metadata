/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_moments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.moments.Quarter;
import org.springframework.modulith.moments.ShiftedQuarter;

import tools.jackson.databind.json.JsonMapper;

public class MomentsJacksonAutoConfigurationTest {

    @Test
    void autoConfiguredMapperRoundTripsShiftedQuarter() {
        ShiftedQuarter quarter = ShiftedQuarter.of(Quarter.Q4, Month.FEBRUARY);

        try (ConfigurableApplicationContext context = SpringApplication.run(JacksonApplication.class,
                "--spring.main.banner-mode=off",
                "--spring.main.web-application-type=none",
                "--spring.modulith.moments.enabled=false")) {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            String json = mapper.writeValueAsString(quarter);
            ShiftedQuarter restored = mapper.readValue(json, ShiftedQuarter.class);

            assertThat(json)
                    .contains("\"quarter\"", "\"startMonth\"")
                    .doesNotContain("\"start\"", "\"end\"");
            assertThat(restored).isEqualTo(quarter);
            assertThat(restored.getStartDate(Year.of(2026))).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(restored.getEndDate(Year.of(2026))).isEqualTo(LocalDate.of(2027, 1, 31));
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class JacksonApplication {
    }
}
