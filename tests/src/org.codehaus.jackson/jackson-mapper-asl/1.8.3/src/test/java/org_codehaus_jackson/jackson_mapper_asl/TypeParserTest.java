/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeParserTest {
    @Test
    void parsesCanonicalParameterizedTypeNames() {
        String canonicalType = "java.util.LinkedHashMap<java.lang.String,java.util.ArrayList<java.lang.Integer>>";

        JavaType javaType = TypeFactory.defaultInstance().constructFromCanonical(canonicalType);

        assertThat(javaType.getRawClass()).isEqualTo(LinkedHashMap.class);
        assertThat(javaType.getKeyType().getRawClass()).isEqualTo(String.class);
        assertThat(javaType.getContentType().getRawClass()).isEqualTo(ArrayList.class);
        assertThat(javaType.getContentType().getContentType().getRawClass()).isEqualTo(Integer.class);
        assertThat(javaType.toCanonical()).isEqualTo(canonicalType);
    }
}
