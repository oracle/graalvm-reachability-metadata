/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArrayDeserializerInnerObjectArrayReferringAccumulatorTest {

    @Test
    void deserializesTypedObjectIdElementsIntoTypedArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        IdentifiedValue[] values = mapper.readValue("""
                [
                  {"id": 1, "name": "first"},
                  {"id": 2, "name": "second"}
                ]
                """, IdentifiedValue[].class);

        assertThat(values)
                .isInstanceOf(IdentifiedValue[].class)
                .extracting(value -> value.name)
                .containsExactly("first", "second");
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class IdentifiedValue {
        public int id;
        public String name;
    }
}
