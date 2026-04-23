/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonObjectArrayDeserializerTest {

    @Test
    void objectArrayDeserializerCreatesTypedSingleElementArrays() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        ArrayElement[] values = mapper.readValue("{\"name\":\"only\"}", ArrayElement[].class);
        assertThat(values).hasSize(1);
        assertThat(values[0].name).isEqualTo("only");
    }

    public static class ArrayElement {

        public String name;
    }
}
