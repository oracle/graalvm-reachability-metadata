/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class ArrayDeserializerTest {
    @Test
    public void deserializesSingleBeanValueAsTypedObjectArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        Value[] values = mapper.readValue("{\"name\":\"single\"}", Value[].class);

        assertThat(values).hasSize(1);
        assertThat(values.getClass().getComponentType()).isEqualTo(Value.class);
        assertThat(values[0].name).isEqualTo("single");
    }

    public static final class Value {
        public String name;
    }
}
