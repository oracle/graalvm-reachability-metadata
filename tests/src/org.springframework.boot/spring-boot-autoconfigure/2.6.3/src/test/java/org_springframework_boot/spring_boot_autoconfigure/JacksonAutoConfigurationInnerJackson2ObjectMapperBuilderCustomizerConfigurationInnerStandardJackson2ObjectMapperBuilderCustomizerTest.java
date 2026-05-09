/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAutoConfigurationInnerJackson2ObjectMapperBuilderCustomizerConfigurationInnerStandardJackson2ObjectMapperBuilderCustomizerTest {

    @Test
    void propertyNamingStrategyConstantConfiguresObjectMapper() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.jackson.property-naming-strategy", "SNAKE_CASE")));
            context.register(JacksonAutoConfiguration.class);

            context.refresh();

            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            String json = objectMapper.writeValueAsString(new SamplePayload("configured"));
            assertThat(json).contains("\"sample_value\":\"configured\"");
        }
    }

    public static final class SamplePayload {

        private final String sampleValue;

        private SamplePayload(String sampleValue) {
            this.sampleValue = sampleValue;
        }

        public String getSampleValue() {
            return this.sampleValue;
        }

    }

}
