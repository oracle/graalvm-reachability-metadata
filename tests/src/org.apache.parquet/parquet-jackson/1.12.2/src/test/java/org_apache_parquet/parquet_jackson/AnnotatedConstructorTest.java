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

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.BeanDescription;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;

public class AnnotatedConstructorTest {
    @Test
    void invokesDefaultConstructorViaCall() throws Exception {
        AnnotatedConstructor constructor = beanDescription().findDefaultConstructor();

        Object value = constructor.call();

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).describe()).isEqualTo("default:0");
    }

    @Test
    void invokesMultiArgumentConstructorViaCallWithArray() throws Exception {
        AnnotatedConstructor constructor = constructorWithParameters(String.class, int.class);

        Object value = constructor.call(new Object[] { "named", 42 });

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).describe()).isEqualTo("named:42");
    }

    @Test
    void invokesSingleArgumentConstructorViaCall1() throws Exception {
        AnnotatedConstructor constructor = constructorWithParameters(String.class);

        Object value = constructor.call1("single");

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).describe()).isEqualTo("single:1");
    }

    @Test
    void resolvesConstructorAfterJavaSerializationRoundTrip() throws Exception {
        AnnotatedConstructor original = beanDescription().findDefaultConstructor();

        AnnotatedConstructor restored = serializeAndDeserialize(original);

        assertThat(restored.getDeclaringClass()).isEqualTo(ConstructorTarget.class);
        assertThat(restored.call()).isInstanceOf(ConstructorTarget.class);
    }

    private static AnnotatedConstructor constructorWithParameters(Class<?>... parameterTypes) {
        for (AnnotatedConstructor constructor : beanDescription().getConstructors()) {
            if (hasParameterTypes(constructor, parameterTypes)) {
                return constructor;
            }
        }
        throw new AssertionError("Missing constructor with " + parameterTypes.length + " parameters");
    }

    private static boolean hasParameterTypes(AnnotatedConstructor constructor, Class<?>[] parameterTypes) {
        if (constructor.getParameterCount() != parameterTypes.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!parameterTypes[index].equals(constructor.getRawParameterType(index))) {
                return false;
            }
        }
        return true;
    }

    private static BeanDescription beanDescription() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.getDeserializationConfig().introspect(mapper.constructType(ConstructorTarget.class));
    }

    private static AnnotatedConstructor serializeAndDeserialize(AnnotatedConstructor constructor) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(constructor);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (AnnotatedConstructor) objectInput.readObject();
        }
    }

    public static final class ConstructorTarget {
        private final String name;
        private final int count;

        public ConstructorTarget() {
            this("default", 0);
        }

        public ConstructorTarget(String name) {
            this(name, 1);
        }

        public ConstructorTarget(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String describe() {
            return name + ":" + count;
        }
    }
}
