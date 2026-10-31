/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_openai.openai_java_core;

import com.openai.core.ObjectMappers;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Openai_java_coreTest {
    @Test
    void serializesInputStreamAsBase64Json() throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(
                "hello".getBytes(StandardCharsets.UTF_8))) {
            assertEquals("\"aGVsbG8=\"", ObjectMappers.jsonMapper().writeValueAsString(input));
        }
    }
}
