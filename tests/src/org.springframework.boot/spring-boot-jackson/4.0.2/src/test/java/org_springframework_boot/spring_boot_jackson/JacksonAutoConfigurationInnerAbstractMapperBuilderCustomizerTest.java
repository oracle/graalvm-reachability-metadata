/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JacksonAutoConfigurationInnerAbstractMapperBuilderCustomizerTest {
    @Test
    void appliesConfiguredNamingStrategyConstantToJsonProperties() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "jacksonNaming", Map.of("spring.jackson.property-naming-strategy", "SNAKE_CASE")));
            context.register(JacksonAutoConfiguration.class);
            context.refresh();
            assertEquals("SNAKE_CASE", context.getBean(JacksonProperties.class).getPropertyNamingStrategy());
            JsonMapper mapper = context.getBean(JsonMapper.class);
            CustomerProfile profile = new CustomerProfile("Robin", "South Harbor");

            String json = mapper.writeValueAsString(profile);
            JsonNode document = mapper.readTree(json);

            assertEquals(mapper.readTree("""
                    {"first_name":"Robin","home_city":"South Harbor"}
                    """), document);
            assertFalse(document.has("firstName"));
            assertFalse(document.has("homeCity"));
            assertEquals(profile, mapper.readValue(json, CustomerProfile.class));
            assertEquals(new CustomerProfile("Alex", "North Point"), mapper.readValue("""
                    {"first_name":"Alex","home_city":"North Point"}
                    """, CustomerProfile.class));
        }
    }

    public record CustomerProfile(String firstName, String homeCity) {
    }
}
