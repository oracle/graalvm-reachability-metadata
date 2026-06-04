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

class ReflectionCacheKtTest {
    @Test
    void deserializesPrimaryConstructorWithValueClassParameter() throws Exception {
        ObjectMapper objectMapper = newObjectMapper();

        ReflectionCacheValueClassHolder result = objectMapper.readValue(
                "{\"wrapped\":\"alpha\"}",
                ReflectionCacheValueClassHolder.class);

        assertThat(result.wrappedValue()).isEqualTo("alpha");
    }

    private static ObjectMapper newObjectMapper() {
        return JsonMapper.builder()
                .addModule(new KotlinModule.Builder().build())
                .build();
    }
}
