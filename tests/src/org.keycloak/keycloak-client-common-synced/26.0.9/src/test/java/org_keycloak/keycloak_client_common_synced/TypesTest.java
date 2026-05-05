/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Types;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    void findsImplementingMethodUsingResolvedGenericParameterType() throws NoSuchMethodException {
        Method interfaceMethod = GenericProcessor.class.getMethod("process", Object.class);

        Method implementingMethod = Types.getImplementingMethod(StringProcessor.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(StringProcessor.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void fallsBackToErasedParameterTypesForRawGenericImplementation() throws NoSuchMethodException {
        Method interfaceMethod = GenericProcessor.class.getMethod("process", Object.class);

        Method implementingMethod = Types.getImplementingMethod(RawProcessor.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(RawProcessor.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(Object.class);
    }

    @Test
    void resolvesGenericArrayTypeToRawArrayClass() throws NoSuchFieldException {
        Field values = GenericArrayHolder.class.getField("values");
        Type genericArrayType = values.getGenericType();

        Class<?> rawType = Types.getRawType(genericArrayType);

        assertThat(rawType).isEqualTo(Object[].class);
    }

    @Test
    void resolvesGenericArrayTypeToRawArrayClassWithoutException() throws NoSuchFieldException {
        Field values = GenericArrayHolder.class.getField("values");
        Type genericArrayType = values.getGenericType();

        Class<?> rawType = Types.getRawTypeNoException(genericArrayType);

        assertThat(rawType).isEqualTo(Object[].class);
    }

    public interface GenericProcessor<T> {
        T process(T value);
    }

    public static class StringProcessor implements GenericProcessor<String> {
        @Override
        public String process(String value) {
            return "string:" + value;
        }
    }

    @SuppressWarnings("rawtypes")
    public static class RawProcessor implements GenericProcessor {
        @Override
        public Object process(Object value) {
            return value;
        }
    }

    public static class GenericArrayHolder<T> {
        public T[] values;
    }
}
