/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBuildersTest {

    @Test
    void registersAdditionalDeserializers() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Token.class, new JsonDeserializer<Token>() {
            @Override
            public Token deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return new Token("decoded:" + parser.getText());
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        Token token = mapper.readValue("\"node\"", Token.class);

        assertThat(token).isEqualTo(new Token("decoded:node"));
    }

    @Test
    void registersAdditionalKeyDeserializers() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addKeyDeserializer(Token.class, new KeyDeserializer() {
            @Override
            public Token deserializeKey(String key, DeserializationContext context) {
                return new Token(key.substring("id:".length()));
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        Map<Token, Integer> values = mapper.readValue("""
                {"id:node": 7, "id:backup": 3}
                """, new TypeReference<Map<Token, Integer>>() { });

        assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of(
                new Token("node"), 7, new Token("backup"), 3));
    }

    @Test
    void registersBeanDeserializerModifiers() throws Exception {
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                    BeanDescription description, BeanDeserializerBuilder builder) {
                if (description.getBeanClass() == Settings.class) {
                    builder.removeProperty(PropertyName.construct("secret"));
                    builder.addIgnorable("secret");
                }
                return builder;
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        Settings settings = mapper.readValue("""
                {"name": "primary", "secret": "not imported"}
                """, Settings.class);

        assertThat(settings.name).isEqualTo("primary");
        assertThat(settings.secret).isNull();
    }

    @Test
    void registersValueInstantiators() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addValueInstantiator(Settings.class, new ValueInstantiator.Base(Settings.class) {
            @Override
            public boolean canCreateUsingDefault() {
                return true;
            }

            @Override
            public Settings createUsingDefault(DeserializationContext context) {
                Settings settings = new Settings();
                settings.secret = "module-default";
                return settings;
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        Settings settings = mapper.readValue("""
                {"name": "primary"}
                """, Settings.class);

        assertThat(settings.name).isEqualTo("primary");
        assertThat(settings.secret).isEqualTo("module-default");
    }

    @Test
    void registersAdditionalSerializers() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Token.class, new JsonSerializer<Token>() {
            @Override
            public void serialize(Token token, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                generator.writeString("encoded:" + token.value());
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        String json = mapper.writeValueAsString(new Token("node"));

        assertThat(json).isEqualTo("\"encoded:node\"");
    }

    @Test
    void registersBeanSerializerModifiers() throws Exception {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription description, List<BeanPropertyWriter> properties) {
                if (description.getBeanClass() == Settings.class) {
                    properties.removeIf(property -> property.getName().equals("secret"));
                }
                return properties;
            }
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(module);
        Settings settings = new Settings();
        settings.name = "primary";
        settings.secret = "not exported";

        String json = mapper.writeValueAsString(settings);

        assertThat(json).isEqualTo("{\"name\":\"primary\"}");
    }

    public record Token(String value) { }

    public static class Settings {
        public String name;
        public String secret;
    }
}
