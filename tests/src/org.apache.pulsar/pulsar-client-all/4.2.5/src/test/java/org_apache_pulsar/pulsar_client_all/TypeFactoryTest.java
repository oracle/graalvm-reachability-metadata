/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.JavaType;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

public class TypeFactoryTest {
    @Test
    void resolvesCanonicalAndArrayTypes() throws Exception {
        final TypeFactory factory = TypeFactory.defaultInstance();
        final JavaType listType = factory.constructFromCanonical("java.util.List<java.lang.String>");
        final JavaType arrayType = factory.constructArrayType(String.class);

        assertThat(listType.getRawClass()).isEqualTo(List.class);
        assertThat(listType.getContentType().getRawClass()).isEqualTo(String.class);
        assertThat(arrayType.getRawClass()).isEqualTo(String[].class);
        assertThat(factory.findClass("java.lang.Integer")).isEqualTo(Integer.class);
    }
}
