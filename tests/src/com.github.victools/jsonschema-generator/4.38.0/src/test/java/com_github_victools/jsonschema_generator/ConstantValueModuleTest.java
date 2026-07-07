/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_generator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstantValueModuleTest {
    @Test
    void constantFieldValuesAreExtractedForPublicAndPrivateFields() {
        SchemaGeneratorConfig config = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.FULL_DOCUMENTATION)
                .build();
        ObjectNode schema = new SchemaGenerator(config).generateSchema(ConstantFields.class);

        assertThat(schema.toString())
                .contains("public constant")
                .contains("private constant");
    }

    public static class ConstantFields {
        public static final String PUBLIC_CONSTANT = "public constant";
        private static final String PRIVATE_CONSTANT = "private constant";

        private ConstantFields() { }
    }
}
