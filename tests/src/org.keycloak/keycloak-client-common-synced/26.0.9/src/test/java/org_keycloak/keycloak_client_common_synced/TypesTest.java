/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Types;

public class TypesTest {
    @Test
    void resolvesGenericInterfaceMethodToConcreteImplementation() throws Exception {
        Method interfaceMethod = ValueSink.class.getMethod("accept", Object.class);

        Method implementingMethod = Types.getImplementingMethod(StringValueSink.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(StringValueSink.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void fallsBackToErasedGenericInterfaceMethodOnRawImplementation() throws Exception {
        Method interfaceMethod = ValueSink.class.getMethod("accept", Object.class);

        Method implementingMethod = Types.getImplementingMethod(RawValueSink.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(RawValueSink.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(Object.class);
    }

    @Test
    void resolvesRawClassForGenericArrayTypes() throws Exception {
        Field field = GenericArrayFixture.class.getDeclaredField("lists");

        assertThat(Types.getRawType(field.getGenericType())).isEqualTo(List[].class);
        assertThat(Types.getRawTypeNoException(field.getGenericType())).isEqualTo(List[].class);
    }

    private interface ValueSink<T> {
        void accept(T value);
    }

    private static final class StringValueSink implements ValueSink<String> {
        @Override
        public void accept(String value) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class RawValueSink implements ValueSink {
        @Override
        public void accept(Object value) {
        }
    }

    private static final class GenericArrayFixture {
        private List<String>[] lists;
    }
}
