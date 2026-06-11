/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

public class TypeParserTest {
    @Test
    public void constructsParameterizedTypeFromCanonicalName() {
        JavaType type = TypeFactory.defaultInstance().constructFromCanonical(
                "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>");

        assertThat(type.getRawClass()).isEqualTo(Map.class);
        assertThat(type.getKeyType().getRawClass()).isEqualTo(String.class);
        assertThat(type.getContentType().getRawClass()).isEqualTo(List.class);
        assertThat(type.getContentType().getContentType().getRawClass()).isEqualTo(Integer.class);
    }
}
