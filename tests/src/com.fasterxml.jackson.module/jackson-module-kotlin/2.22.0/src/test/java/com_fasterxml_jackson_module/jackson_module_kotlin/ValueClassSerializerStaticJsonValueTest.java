/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueClassSerializerStaticJsonValueTest {
    @Test
    void serializesValueClassUsingStaticJsonValueMethod() throws Exception {
        ObjectMapper objectMapper = newObjectMapper();

        String json = objectMapper.writeValueAsString(
                ValueClassSerializerStaticJsonValueFixtures.boxedStaticJsonValueExample("alpha"));

        assertThat(json).isEqualTo("\"json:alpha\"");
    }

    @Test
    void serializesNullWhenStaticJsonValueMethodReturnsNull() throws Exception {
        ObjectMapper objectMapper = newObjectMapper();

        String json = objectMapper.writeValueAsString(
                ValueClassSerializerStaticJsonValueFixtures.boxedStaticJsonValueExample("emit-null"));

        assertThat(json).isEqualTo("null");
    }

    private static ObjectMapper newObjectMapper() {
        return JsonMapper.builder()
                .addModule(new KotlinModule.Builder().build())
                .build();
    }
}
