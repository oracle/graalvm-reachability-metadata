/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

public class Jackson2AutoConfigurationInnerJackson2ObjectMapperBuilderCustomizerConfigurationInnerStandardJackson2ObjectMapperBuilderCustomizerTest {
    @Test
    void appliesPropertyNamingStrategyConfiguredByJackson2PropertyFieldName() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties",
                    Map.of("spring.jackson2.property-naming-strategy", "SNAKE_CASE")));
            context.register(Jackson2AutoConfiguration.class);
            context.refresh();

            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            assertThat(objectMapper.writeValueAsString(new NamingExample("Jane", "Doe")))
                    .isEqualTo("{\"first_name\":\"Jane\",\"last_name\":\"Doe\"}");
        }
    }

    public static final class NamingExample {
        private final String firstName;

        private final String lastName;

        NamingExample(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return this.firstName;
        }

        public String getLastName() {
            return this.lastName;
        }
    }
}
