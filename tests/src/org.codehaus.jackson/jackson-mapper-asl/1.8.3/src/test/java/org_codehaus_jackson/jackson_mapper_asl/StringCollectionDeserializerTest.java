/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.LinkedList;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringCollectionDeserializerTest {
    @Test
    void concreteStringCollectionIsCreatedWithDefaultConstructorForJsonArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType stringListType = mapper.getTypeFactory().constructCollectionType(LinkedList.class, String.class);

        Object result = mapper.readValue("[\"alpha\",null,\"omega\"]", stringListType);

        assertThat(result).isInstanceOf(LinkedList.class);
        LinkedList<?> values = (LinkedList<?>) result;
        assertThat(values).hasSize(3);
        assertThat(values.get(0)).isEqualTo("alpha");
        assertThat(values.get(1)).isNull();
        assertThat(values.get(2)).isEqualTo("omega");
    }

    @Test
    void concreteStringCollectionIsCreatedBeforeCoercingSingleValueToArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JavaType stringListType = mapper.getTypeFactory().constructCollectionType(LinkedList.class, String.class);

        Object result = mapper.readValue("\"single\"", stringListType);

        assertThat(result).isInstanceOf(LinkedList.class);
        LinkedList<?> values = (LinkedList<?>) result;
        assertThat(values).hasSize(1);
        assertThat(values.get(0)).isEqualTo("single");
    }
}
