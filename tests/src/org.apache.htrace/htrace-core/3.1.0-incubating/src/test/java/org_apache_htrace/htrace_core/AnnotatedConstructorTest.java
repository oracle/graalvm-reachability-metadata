/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.htrace.fasterxml.jackson.annotation.JsonCreator;
import org.apache.htrace.fasterxml.jackson.annotation.JsonProperty;
import org.apache.htrace.fasterxml.jackson.databind.BeanDescription;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.apache.htrace.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedConstructorTest {
    @Test
    void invokesOneArgumentConstructorThroughAnnotatedConstructor() throws Exception {
        AnnotatedConstructor constructor = findConstructor(ConstructorBackedBean.class, String.class);

        ConstructorBackedBean bean = (ConstructorBackedBean) constructor.call1("created-through-call1");

        assertThat(constructor.getParameterCount()).isEqualTo(1);
        assertThat(constructor.getRawParameterType(0)).isEqualTo(String.class);
        assertThat(bean.getValue()).isEqualTo("created-through-call1");
    }

    @Test
    void resolvesSerializedAnnotatedConstructorBackToDeclaredConstructor() throws Exception {
        AnnotatedConstructor constructor = findConstructor(ConstructorBackedBean.class, String.class);

        AnnotatedConstructor restored = roundTripThroughJavaSerialization(constructor);
        ConstructorBackedBean bean = (ConstructorBackedBean) restored.call1("resolved-constructor");

        assertThat(restored.getDeclaringClass()).isEqualTo(ConstructorBackedBean.class);
        assertThat(restored.getParameterCount()).isEqualTo(1);
        assertThat(restored.getRawParameterType(0)).isEqualTo(String.class);
        assertThat(bean.getValue()).isEqualTo("resolved-constructor");
    }

    private static AnnotatedConstructor findConstructor(Class<?> beanClass, Class<?>... parameterTypes) {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(beanClass));

        for (AnnotatedConstructor constructor : description.getConstructors()) {
            if (hasParameterTypes(constructor, parameterTypes)) {
                return constructor;
            }
        }
        throw new IllegalArgumentException("Missing constructor for: " + beanClass.getName());
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

    private static AnnotatedConstructor roundTripThroughJavaSerialization(AnnotatedConstructor constructor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(constructor);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AnnotatedConstructor) input.readObject();
        }
    }

    public static class ConstructorBackedBean {
        private final String value;

        @JsonCreator
        public ConstructorBackedBean(@JsonProperty("value") String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
