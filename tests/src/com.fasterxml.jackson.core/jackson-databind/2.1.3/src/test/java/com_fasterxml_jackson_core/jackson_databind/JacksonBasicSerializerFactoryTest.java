/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBasicSerializerFactoryTest {

    @Test
    void basicSerializerFactoryInstantiatesLazySerializerImplementations() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TokenBuffer buffer = new TokenBuffer(null);
        buffer.writeStartObject();
        buffer.writeStringField("value", "buffered");
        buffer.writeEndObject();

        assertThat(mapper.writeValueAsString(buffer)).isEqualTo("{\"value\":\"buffered\"}");
    }
}
