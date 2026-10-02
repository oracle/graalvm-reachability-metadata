/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_erosb.everit_json_schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class ClassPathAwareSchemaClientTest {
    @Test
    void loadsAndUsesSchemaFromClasspath() {
        JSONObject schemaJson = new JSONObject()
                .put("$ref", "classpath:/schemas/person.json");

        Schema schema = SchemaLoader.load(schemaJson, SchemaClient.classPathAwareClient());

        assertThatCode(() -> schema.validate(new JSONObject().put("name", "Ada")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> schema.validate(new JSONObject().put("name", 42)))
                .isInstanceOf(ValidationException.class);
    }
}
