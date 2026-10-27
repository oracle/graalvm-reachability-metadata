/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.JavaType;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

public class TypeFactoryTest {
    @Test
    void resolvesCanonicalCollectionAndArrayTypes() throws Exception {
        TypeFactory factory = TypeFactory.defaultInstance();

        JavaType listType = factory.constructFromCanonical("java.util.List<java.lang.String>");
        JavaType arrayType = factory.constructArrayType(String.class);
        JavaType applicationType = factory.constructFromCanonical(Payload.class.getName());
        Type genericArray = GenericArrayHolder.class.getField("values").getGenericType();

        assertThat(listType.getRawClass()).isEqualTo(List.class);
        assertThat(listType.getContentType().getRawClass()).isEqualTo(String.class);
        assertThat(arrayType.getRawClass()).isEqualTo(String[].class);
        assertThat(applicationType.getRawClass()).isEqualTo(Payload.class);
        assertThat(factory.findClass(Payload.class.getName())).isEqualTo(Payload.class);
        assertThat(TypeFactory.rawClass(genericArray)).isEqualTo(Object[].class);
    }

    public static class Payload {
    }

    public static class GenericArrayHolder<T> {
        public T[] values;
    }
}
