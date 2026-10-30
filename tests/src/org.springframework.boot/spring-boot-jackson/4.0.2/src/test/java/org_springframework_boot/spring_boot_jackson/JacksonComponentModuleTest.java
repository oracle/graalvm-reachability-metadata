/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.jackson.JacksonComponentModule;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonComponentModuleTest {
    @Test
    void discoversNestedCodecsFromAnnotatedComponent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("productCodeCodec", new ProductCodeComponent());
        JacksonComponentModule module = new JacksonComponentModule();
        module.setBeanFactory(beanFactory);
        module.afterPropertiesSet();
        JsonMapper mapper = JsonMapper.builder().addModule(module).build();
        ProductCode code = new ProductCode("lamp-17");

        String json = mapper.writeValueAsString(code);

        assertEquals("\"sku:lamp-17\"", json);
        assertEquals(code, mapper.readValue(json, ProductCode.class));
        assertEquals(new ProductCode("desk-42"), mapper.readValue("\"sku:desk-42\"", ProductCode.class));
    }

    public record ProductCode(String value) {
    }

    @JacksonComponent
    public static class ProductCodeComponent {
        public static class Serializer extends ValueSerializer<ProductCode> {
            @Override
            public void serialize(ProductCode value, JsonGenerator generator, SerializationContext context) {
                generator.writeString("sku:" + value.value());
            }
        }

        public static class Deserializer extends ValueDeserializer<ProductCode> {
            @Override
            public ProductCode deserialize(JsonParser parser, DeserializationContext context) {
                return new ProductCode(parser.getString().substring("sku:".length()));
            }
        }
    }
}
