/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.TypeFactory;

public class ArrayBuildersTest {
    @Test
    void laterModuleProvidersControlMapKeysValuesAndBeanConstruction() {
        SimpleModule defaults = inventoryModule("defaults", "legacy:");
        SimpleModule preferred = inventoryModule("preferred", "sku:");
        ObjectMapper mapper = JsonMapper.builder()
                .addModules(defaults, preferred)
                .build();

        Map<ItemCode, Inventory> inventory = mapper.readValue(
                """
                {
                  "sku:part-7": {"item": "sku:part-7", "quantity": 12},
                  "sku:part-8": {"item": "sku:part-8", "quantity": 3}
                }
                """,
                new TypeReference<Map<ItemCode, Inventory>>() {
                });

        assertThat(inventory).containsOnlyKeys(new ItemCode("part-7"), new ItemCode("part-8"));
        Inventory first = inventory.get(new ItemCode("part-7"));
        assertThat(first.item).isEqualTo(new ItemCode("part-7"));
        assertThat(first.quantity).isEqualTo(12);
        assertThat(first.getCreatedBy()).isEqualTo("preferred");
        Inventory second = inventory.get(new ItemCode("part-8"));
        assertThat(second.item).isEqualTo(new ItemCode("part-8"));
        assertThat(second.quantity).isEqualTo(3);
        assertThat(second.getCreatedBy()).isEqualTo("preferred");

        JsonNode written = mapper.readTree(mapper.writeValueAsString(inventory));
        assertThat(written.size()).isEqualTo(2);
        assertThat(written.at("/sku:part-7/item").asText()).isEqualTo("sku:part-7");
        assertThat(written.at("/sku:part-7/quantity").asInt()).isEqualTo(12);
        assertThat(written.at("/sku:part-8/item").asText()).isEqualTo("sku:part-8");
        assertThat(written.at("/sku:part-8/quantity").asInt()).isEqualTo(3);
    }

    private static SimpleModule inventoryModule(String name, String prefix) {
        return new SimpleModule(name)
                .addSerializer(ItemCode.class, new ItemCodeSerializer(prefix))
                .addDeserializer(ItemCode.class, new ItemCodeDeserializer(prefix))
                .addKeySerializer(ItemCode.class, new ItemCodeKeySerializer(prefix))
                .addKeyDeserializer(ItemCode.class, new ItemCodeKeyDeserializer(prefix))
                .addValueInstantiator(Inventory.class, new InventoryInstantiator(name));
    }

    public record ItemCode(String value) {
    }

    public static final class Inventory {
        private final String createdBy;
        public ItemCode item;
        public int quantity;

        public Inventory(String createdBy) {
            this.createdBy = createdBy;
        }

        public String getCreatedBy() {
            return createdBy;
        }
    }

    public static final class ItemCodeSerializer extends ValueSerializer<ItemCode> {
        private final String prefix;

        public ItemCodeSerializer(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void serialize(ItemCode value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(prefix + value.value());
        }
    }

    public static final class ItemCodeDeserializer extends ValueDeserializer<ItemCode> {
        private final String prefix;

        public ItemCodeDeserializer(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public ItemCode deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return new ItemCode(parser.getString().substring(prefix.length()));
        }
    }

    public static final class ItemCodeKeySerializer extends ValueSerializer<ItemCode> {
        private final String prefix;

        public ItemCodeKeySerializer(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void serialize(ItemCode value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeName(prefix + value.value());
        }
    }

    public static final class ItemCodeKeyDeserializer extends KeyDeserializer {
        private final String prefix;

        public ItemCodeKeyDeserializer(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Object deserializeKey(String key, DeserializationContext context) {
            return new ItemCode(key.substring(prefix.length()));
        }
    }

    public static final class InventoryInstantiator extends StdValueInstantiator {
        private final String createdBy;

        public InventoryInstantiator(String createdBy) {
            super(null, TypeFactory.createDefaultInstance().constructType(Inventory.class));
            this.createdBy = createdBy;
        }

        @Override
        public boolean canCreateUsingDefault() {
            return true;
        }

        @Override
        public Object createUsingDefault(DeserializationContext context) {
            return new Inventory(createdBy);
        }
    }
}
