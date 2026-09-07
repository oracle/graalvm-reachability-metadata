/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.DeserializationFeature;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArrayDeserializerTest {
    @Test
    void wrapsASingleJsonValueInATypedArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        Value[] values = mapper.readValue("{\"name\":\"single\"}", Value[].class);

        assertThat(values).extracting(value -> value.name).containsExactly("single");
    }

    public static class Value {
        public String name;
    }
}
