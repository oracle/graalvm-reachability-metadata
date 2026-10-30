/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentConverterTest {

    @Test
    void convertsStandardEnvironmentToApplicationEnvironment() {
        StandardEnvironment source = new StandardEnvironment();
        source.setActiveProfiles("cloud");
        source.getPropertySources()
                .addFirst(new MapPropertySource("deployment", Collections.singletonMap("demo.region", "eu-west")));

        ConvertingApplication.main(new String[0]);
        StandardEnvironment converted = ConvertingApplication.convert(source);

        assertThat(converted).isNotSameAs(source);
        assertThat(converted.getClass()).isNotEqualTo(StandardEnvironment.class);
        assertThat(converted.getActiveProfiles()).containsExactly("cloud");
        assertThat(converted.getProperty("demo.region")).isEqualTo("eu-west");
        assertThat(converted.getPropertySources().contains("deployment")).isTrue();
        assertThat(converted.getConversionService()).isSameAs(source.getConversionService());
    }

    public static class ConvertingApplication {

        static final AtomicReference<SpringApplication> APPLICATION = new AtomicReference<>();

        public static void main(String[] args) {
            APPLICATION.set(new SpringApplication(ConvertingApplication.class));
        }

        @SuppressWarnings("deprecation")
        static StandardEnvironment convert(StandardEnvironment environment) {
            return APPLICATION.get().convertEnvironment(environment);
        }
    }
}
