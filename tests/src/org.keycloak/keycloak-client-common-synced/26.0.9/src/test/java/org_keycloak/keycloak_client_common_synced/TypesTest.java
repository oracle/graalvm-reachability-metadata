/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    void findsImplementingMethodAfterResolvingGenericInterfaceParameter() throws Exception {
        Method interfaceMethod = ValueConsumer.class.getMethod("consume", Object.class);

        Method implementingMethod = Types.getImplementingMethod(StringValueConsumer.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(StringValueConsumer.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void fallsBackToErasedInterfaceMethodParameterForRawImplementations() throws Exception {
        Method interfaceMethod = ValueConsumer.class.getMethod("consume", Object.class);

        Method implementingMethod = Types.getImplementingMethod(RawValueConsumer.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(RawValueConsumer.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(Object.class);
    }

    @Test
    void resolvesGenericArrayTypesToRawArrayClasses() throws Exception {
        Field valuesField = GenericArrayHolder.class.getDeclaredField("values");
        Type genericType = valuesField.getGenericType();

        assertThat(genericType).isInstanceOf(GenericArrayType.class);
        assertThat(Types.getRawType(genericType)).isEqualTo(List[].class);
        assertThat(Types.getRawTypeNoException(genericType)).isEqualTo(List[].class);
    }

    public interface ValueConsumer<T> {
        void consume(T value);
    }

    public static class StringValueConsumer implements ValueConsumer<String> {
        @Override
        public void consume(String value) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class RawValueConsumer implements ValueConsumer {
        @Override
        public void consume(Object value) {
        }
    }

    public static class GenericArrayHolder {
        private List<String>[] values;
    }
}
