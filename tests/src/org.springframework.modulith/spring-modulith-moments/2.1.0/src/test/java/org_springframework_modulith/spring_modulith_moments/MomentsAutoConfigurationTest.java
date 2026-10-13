/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_moments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.moments.support.Moments;
import org.springframework.modulith.moments.support.MomentsProperties;
import org.springframework.modulith.moments.support.TimeMachine;

public class MomentsAutoConfigurationTest {

    private static final Instant REFERENCE_TIME = Instant.parse("2025-06-15T10:30:00Z");

    @Test
    void configuresTimeMachineWithBoundCalendarSettingsAndApplicationClock() {
        try (ConfigurableApplicationContext context = SpringApplication.run(MomentsApplication.class,
                "--spring.main.banner-mode=off",
                "--spring.main.web-application-type=none",
                "--spring.modulith.moments.enable-time-machine=true",
                "--spring.modulith.moments.locale=en-GB",
                "--spring.modulith.moments.zone-id=Europe/Paris")) {
            TimeMachine timeMachine = context.getBean(TimeMachine.class);
            MomentsProperties properties = context.getBean(MomentsProperties.class);

            assertThat(context.getBean(Moments.class)).isSameAs(timeMachine);
            assertThat(timeMachine.instant()).isEqualTo(REFERENCE_TIME);
            assertThat(timeMachine.now()).isEqualTo(LocalDateTime.of(2025, 6, 15, 12, 30));
            assertThat(properties.isEnableTimeMachine()).isTrue();
            assertThat(properties.getZoneId()).isEqualTo(ZoneId.of("Europe/Paris"));
            assertThat(properties.getLocale()).isEqualTo(Locale.UK);
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class MomentsApplication {

        @Bean
        public Clock applicationClock() {
            return Clock.fixed(REFERENCE_TIME, ZoneOffset.UTC);
        }
    }
}
