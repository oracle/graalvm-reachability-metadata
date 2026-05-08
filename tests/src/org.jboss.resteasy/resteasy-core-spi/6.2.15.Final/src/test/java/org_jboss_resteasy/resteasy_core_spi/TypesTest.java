/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import jakarta.annotation.PostConstruct;

import org.jboss.resteasy.spi.util.Types;
import org.junit.jupiter.api.Test;

public class TypesTest {
    @Test
    void findsImplementingGenericMethodWithResolvedParameterType() throws Exception {
        Method interfaceMethod = GenericEndpoint.class.getMethod("accept", Object.class);

        Method implementingMethod = Types.getImplementingMethod(StringEndpoint.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(StringEndpoint.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void fallsBackToErasedParameterTypesWhenGenericImplementationIsRaw() throws Exception {
        Method interfaceMethod = GenericEndpoint.class.getMethod("accept", Object.class);

        Method implementingMethod = Types.getImplementingMethod(RawEndpoint.class, interfaceMethod);

        assertThat(implementingMethod.getDeclaringClass()).isEqualTo(RawEndpoint.class);
        assertThat(implementingMethod.getParameterTypes()).containsExactly(Object.class);
    }

    @Test
    void findsOverriddenMethodWithMatchingDeclaredSignature() throws Exception {
        Method implementation = ChildResource.class.getDeclaredMethod("handle", String.class);

        Method overriddenMethod = Types.findOverriddenMethod(ChildResource.class, ParentResource.class, implementation);

        assertThat(overriddenMethod).isNotNull();
        assertThat(overriddenMethod.getDeclaringClass()).isEqualTo(ParentResource.class);
        assertThat(overriddenMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void findsOverriddenGenericMethodByResolvingTypeVariables() throws Exception {
        Method implementation = StringResource.class.getDeclaredMethod("store", String.class);

        Method overriddenMethod = Types.findOverriddenMethod(StringResource.class, GenericResource.class, implementation);

        assertThat(overriddenMethod).isNotNull();
        assertThat(overriddenMethod.getDeclaringClass()).isEqualTo(GenericResource.class);
        assertThat(overriddenMethod.getGenericParameterTypes()).containsExactly(GenericResource.class.getTypeParameters()[0]);
    }

    @Test
    void detectsValidPostConstructMethodDeclaredOnClass() {
        boolean hasPostConstruct = Types.hasPostConstruct(ManagedResource.class);

        assertThat(hasPostConstruct).isTrue();
    }

    @Test
    void createsArrayClassForGenericArrayType() throws Exception {
        Type genericArrayType = genericArrayType();

        assertThat(Types.getRawType(genericArrayType)).isEqualTo(CharSequence[].class);
        assertThat(Types.getRawTypeNoException(genericArrayType)).isEqualTo(CharSequence[].class);
    }

    private static Type genericArrayType() throws NoSuchFieldException {
        Field field = GenericArrayHolder.class.getDeclaredField("values");
        return field.getGenericType();
    }

    public interface GenericEndpoint<T> {
        void accept(T value);
    }

    public static class StringEndpoint implements GenericEndpoint<String> {
        @Override
        public void accept(String value) {
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static class RawEndpoint implements GenericEndpoint {
        @Override
        public void accept(Object value) {
        }
    }

    public static class ParentResource {
        public void handle(String value) {
        }
    }

    public static class ChildResource extends ParentResource {
        @Override
        public void handle(String value) {
        }
    }

    public static class GenericResource<T> {
        public void store(T value) {
        }
    }

    public static class StringResource extends GenericResource<String> {
        @Override
        public void store(String value) {
        }
    }

    public static class ManagedResource {
        @PostConstruct
        public void initialize() {
        }
    }

    public static class GenericArrayHolder<T extends CharSequence> {
        public T[] values;
    }
}
