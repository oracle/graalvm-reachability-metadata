/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.LinkedHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapDeserializerTest {
    @Test
    void concreteMapIsCreatedWithDefaultConstructorForJsonObject() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Integer.class);

        LinkedHashMap<String, Integer> result = mapper.readValue("{\"first\":1,\"second\":2}", mapType);

        assertThat(result).isInstanceOf(LinkedHashMap.class);
        assertThat(result).containsEntry("first", Integer.valueOf(1));
        assertThat(result).containsEntry("second", Integer.valueOf(2));
    }
}
