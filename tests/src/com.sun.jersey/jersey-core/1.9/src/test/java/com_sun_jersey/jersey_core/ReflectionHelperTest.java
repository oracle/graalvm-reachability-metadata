/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.reflection.ReflectionHelper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @Test
    public void loadsClassesWithExplicitAndDefaultClassLoaders() throws ClassNotFoundException {
        final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        assertThat(classLoader).isNotNull();

        assertThat(ReflectionHelper.classForName("java.lang.String", classLoader)).isEqualTo(String.class);
        assertThat(ReflectionHelper.classForName("java.lang.String", null)).isEqualTo(String.class);
        assertThat(ReflectionHelper.classForNameWithException("java.lang.String", classLoader)).isEqualTo(String.class);
        assertThat(ReflectionHelper.classForNameWithException("java.lang.String", null)).isEqualTo(String.class);
    }

    @Test
    public void locatesStringConversionMembers() {
        final Method valueOf = ReflectionHelper.getValueOfStringMethod(Integer.class);
        assertThat(valueOf).isNotNull();
        assertThat(valueOf.getName()).isEqualTo("valueOf");
        assertThat(valueOf.getReturnType()).isEqualTo(Integer.class);

        final Method fromString = ReflectionHelper.getFromStringStringMethod(UUID.class);
        assertThat(fromString).isNotNull();
        assertThat(fromString.getName()).isEqualTo("fromString");
        assertThat(fromString.getReturnType()).isEqualTo(UUID.class);

        final Constructor<?> constructor = ReflectionHelper.getStringConstructor(StringBuilder.class);
        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void resolvesArrayClassForComponentType() {
        assertThat(ReflectionHelper.getArrayClass(String.class)).isEqualTo(String[].class);
    }

    @Test
    public void findsMethodsByExactParametersAndCompatibleGenericParameters() throws NoSuchMethodException {
        final Method exactMethod = String.class.getMethod("substring", int.class);
        assertThat(ReflectionHelper.findMethodOnClass(String.class, exactMethod)).isEqualTo(exactMethod);

        final Method stringComparableMethod = String.class.getMethod("compareTo", String.class);
        final Method comparableMethod = ReflectionHelper.findMethodOnClass(Comparable.class, stringComparableMethod);
        assertThat(comparableMethod).isNotNull();
        assertThat(comparableMethod.getName()).isEqualTo("compareTo");
        assertThat(comparableMethod.getParameterTypes()).containsExactly(Object.class);
    }
}
