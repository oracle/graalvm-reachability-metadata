/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.Collections;
import java.util.LinkedList;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionDeserializerTest {
    @Test
    void concreteCollectionIsCreatedWithDefaultConstructorForJsonArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Object result = mapper.readValue("[1,{\"nested\":true},null]", LinkedList.class);

        assertThat(result).isInstanceOf(LinkedList.class);
        LinkedList<?> values = (LinkedList<?>) result;
        assertThat(values).hasSize(3);
        assertThat(values.get(0)).isEqualTo(Integer.valueOf(1));
        assertThat(values.get(1)).isEqualTo(Collections.singletonMap("nested", Boolean.TRUE));
        assertThat(values.get(2)).isNull();
    }

    @Test
    void concreteCollectionIsCreatedBeforeCoercingSingleValueToArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        Object result = mapper.readValue("{\"nested\":true}", LinkedList.class);

        assertThat(result).isInstanceOf(LinkedList.class);
        LinkedList<?> values = (LinkedList<?>) result;
        assertThat(values).hasSize(1);
        assertThat(values.get(0)).isEqualTo(Collections.singletonMap("nested", Boolean.TRUE));
    }
}
