/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class StdDeserializerInnerClassDeserializerTest {
    @Test
    public void deserializesFullyQualifiedClassName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Class<?> deserializedClass = mapper.readValue("\"java.lang.String\"", Class.class);

        assertThat(deserializedClass).isSameAs(String.class);
    }
}
