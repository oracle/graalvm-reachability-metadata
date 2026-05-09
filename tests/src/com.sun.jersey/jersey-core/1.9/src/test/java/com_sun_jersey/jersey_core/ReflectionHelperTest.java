/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.reflection.ReflectionHelper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ReflectionHelperTest {
    @Test
    void resolvesClassesWithProvidedAndDefaultClassLoaders() throws Exception {
        final ClassLoader classLoader = ReflectionHelperTest.class.getClassLoader();
        assertThat(classLoader).isNotNull();

        assertThat(ReflectionHelper.classForName("java.lang.String", classLoader))
                .isEqualTo(String.class);
        assertThat(ReflectionHelper.classForName("java.lang.String", null))
                .isEqualTo(String.class);
        assertThat(ReflectionHelper.classForNameWithException("java.lang.String", classLoader))
                .isEqualTo(String.class);
        assertThat(ReflectionHelper.classForNameWithException("java.lang.String", null))
                .isEqualTo(String.class);
    }

    @Test
    void discoversStringBasedFactoryMethodsAndConstructors() {
        final Method valueOf = ReflectionHelper.getValueOfStringMethod(Integer.class);
        assertThat(valueOf).isNotNull();
        assertThat(valueOf.getName()).isEqualTo("valueOf");
        assertThat(Modifier.isStatic(valueOf.getModifiers())).isTrue();

        final Method fromString = ReflectionHelper.getFromStringStringMethod(UUID.class);
        assertThat(fromString).isNotNull();
        assertThat(fromString.getName()).isEqualTo("fromString");
        assertThat(Modifier.isStatic(fromString.getModifiers())).isTrue();

        final Constructor<?> constructor = ReflectionHelper.getStringConstructor(BigDecimal.class);
        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void createsArrayClassForComponentType() {
        assertThat(ReflectionHelper.getArrayClass(String.class))
                .isEqualTo(String[].class);
    }

    @Test
    void findsMethodsByExactSignatureAndCompatibleGenericSignature() throws Exception {
        final Method size = Collection.class.getMethod("size");
        assertThat(ReflectionHelper.findMethodOnClass(ArrayList.class, size))
                .isNotNull()
                .extracting(Method::getName)
                .isEqualTo("size");

        final Method stringAccept = StringSink.class.getMethod("accept", String.class);
        final Method genericAccept = ReflectionHelper.findMethodOnClass(GenericSink.class, stringAccept);
        assertThat(genericAccept).isNotNull();
        assertThat(genericAccept.getName()).isEqualTo("accept");
        assertThat(genericAccept.getParameterTypes()).containsExactly(Object.class);
    }

    public interface StringSink {
        void accept(String value);
    }

    public static class GenericSink {
        public <T> void accept(T value) {
        }
    }
}
