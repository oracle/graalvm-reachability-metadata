/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.junit.jupiter.api.Test;

public class JacksonModuleTest {

    @Test
    void jsonNamingAnnotationInstantiatesConfiguredPropertyNamingStrategy() {
        ObjectNode schema = generateSchema(NamedFields.class);

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("renamedOriginalName")).isTrue();
        assertThat(properties.has("originalName")).isFalse();
    }

    private static ObjectNode generateSchema(Class<?> targetType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        return generator.generateSchema(targetType);
    }

    @JsonNaming(PrefixNamingStrategy.class)
    public static class NamedFields {
        public String originalName;
    }

    public static class PrefixNamingStrategy extends PropertyNamingStrategy {

        public PrefixNamingStrategy() {
        }

        @Override
        public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
            return "renamed" + defaultName.substring(0, 1).toUpperCase() + defaultName.substring(1);
        }
    }
}
