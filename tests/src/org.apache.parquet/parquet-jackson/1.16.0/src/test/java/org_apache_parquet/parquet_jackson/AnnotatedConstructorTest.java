/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotationMap;

public class AnnotatedConstructorTest {
    @Test
    void invokesConstructorsThroughCallHelpers() throws Exception {
        final AnnotatedConstructor oneArgument = annotatedConstructor(String.class);
        final AnnotatedConstructor twoArguments = annotatedConstructor(String.class, int.class);

        final ConstructedWithOneArgument one =
                (ConstructedWithOneArgument) oneArgument.call1("alpha");
        final ConstructedWithTwoArguments two =
                (ConstructedWithTwoArguments) twoArguments.call(new Object[] {"beta", 7});

        assertThat(one.value()).isEqualTo("alpha");
        assertThat(two.description()).isEqualTo("beta:7");
    }

    @Test
    void restoresSerializedConstructor() throws Exception {
        final AnnotatedConstructor original = annotatedConstructor(String.class, int.class);

        final AnnotatedConstructor restored = roundTrip(original);
        final ConstructedWithTwoArguments value =
                (ConstructedWithTwoArguments) restored.call(new Object[] {"gamma", 11});

        assertThat(restored.getDeclaringClass()).isEqualTo(ConstructedWithTwoArguments.class);
        assertThat(restored.getParameterCount()).isEqualTo(2);
        assertThat(value.description()).isEqualTo("gamma:11");
    }

    private static AnnotatedConstructor annotatedConstructor(Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Class<?> type = parameterTypes.length == 1
                ? ConstructedWithOneArgument.class
                : ConstructedWithTwoArguments.class;
        final Constructor<?> constructor = type.getConstructor(parameterTypes);
        final AnnotationMap[] parameterAnnotations = new AnnotationMap[parameterTypes.length];
        return new AnnotatedConstructor(
                null, constructor, new AnnotationMap(), parameterAnnotations);
    }

    private static AnnotatedConstructor roundTrip(AnnotatedConstructor constructor)
            throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(constructor);
        }

        final ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (AnnotatedConstructor) input.readObject();
        }
    }

    public static final class ConstructedWithOneArgument {
        private final String value;

        public ConstructedWithOneArgument(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class ConstructedWithTwoArguments {
        private final String name;
        private final int count;

        public ConstructedWithTwoArguments(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String description() {
            return name + ":" + count;
        }
    }
}
