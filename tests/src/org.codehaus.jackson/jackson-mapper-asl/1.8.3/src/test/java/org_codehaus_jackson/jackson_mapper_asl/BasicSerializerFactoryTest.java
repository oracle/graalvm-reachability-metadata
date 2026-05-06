/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.TokenBuffer;
import org.junit.jupiter.api.Test;

public class BasicSerializerFactoryTest {
    @Test
    public void serializesTokenBufferUsingLazyStandardSerializer() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TokenBuffer buffer = new TokenBuffer(mapper);
        buffer.writeStartObject();
        buffer.writeFieldName("status");
        buffer.writeString("ok");
        buffer.writeFieldName("count");
        buffer.writeNumber(2);
        buffer.writeEndObject();

        String json = mapper.writeValueAsString(buffer);

        assertThat(json).isEqualTo("{\"status\":\"ok\",\"count\":2}");
    }
}
