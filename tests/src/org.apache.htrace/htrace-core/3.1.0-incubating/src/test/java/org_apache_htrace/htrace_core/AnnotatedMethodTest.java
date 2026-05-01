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
import org.apache.htrace.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.apache.htrace.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMethodTest {
    @Test
    void invokesStaticCreatorMethodsWithNoArgumentsAndOneArgument() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription noArgumentDescription = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(NoArgumentFactoryBean.class));
        BeanDescription oneArgumentDescription = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(OneArgumentFactoryBean.class));
        AnnotatedMethod noArgumentCreator = findFactoryMethod(noArgumentDescription, "create");
        AnnotatedMethod oneArgumentCreator = findFactoryMethod(oneArgumentDescription, "create", String.class);

        NoArgumentFactoryBean noArgumentBean = (NoArgumentFactoryBean) noArgumentCreator.call();
        OneArgumentFactoryBean oneArgumentBean = (OneArgumentFactoryBean) oneArgumentCreator.call1(
                "created-from-argument");

        assertThat(noArgumentBean.getValue()).isEqualTo("created-without-arguments");
        assertThat(oneArgumentBean.getValue()).isEqualTo("created-from-argument");
    }

    @Test
    void invokesMethodGetterAndSetterThroughAnnotatedAccessors() {
        ObjectMapper mapper = new ObjectMapper();
        AccessorBean bean = new AccessorBean("read-through-getter");
        AccessorBean restored = new AccessorBean();
        BeanDescription serializationDescription = mapper.getSerializationConfig()
                .introspect(mapper.constructType(AccessorBean.class));
        BeanDescription deserializationDescription = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(AccessorBean.class));
        AnnotatedMethod getter = findProperty(serializationDescription, "value").getGetter();
        AnnotatedMethod setter = findProperty(deserializationDescription, "value").getSetter();

        setter.setValue(restored, "written-through-setter");

        assertThat(getter.getValue(bean)).isEqualTo("read-through-getter");
        assertThat(restored.getValue()).isEqualTo("written-through-setter");
    }

    @Test
    void resolvesSerializedAnnotatedMethodBackToDeclaredMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(AccessorBean.class));
        AnnotatedMethod getter = findProperty(description, "value").getGetter();

        AnnotatedMethod restored = roundTripThroughJavaSerialization(getter);

        assertThat(restored.getName()).isEqualTo("getValue");
        assertThat(restored.getValue(new AccessorBean("resolved-method"))).isEqualTo("resolved-method");
    }

    private static AnnotatedMethod findFactoryMethod(
            BeanDescription description, String methodName, Class<?>... parameterTypes) {
        for (AnnotatedMethod method : description.getFactoryMethods()) {
            if (methodName.equals(method.getName()) && hasParameterTypes(method, parameterTypes)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Missing factory method: " + methodName);
    }

    private static boolean hasParameterTypes(AnnotatedMethod method, Class<?>[] parameterTypes) {
        if (method.getParameterCount() != parameterTypes.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!parameterTypes[index].equals(method.getRawParameterType(index))) {
                return false;
            }
        }
        return true;
    }

    private static BeanPropertyDefinition findProperty(BeanDescription description, String propertyName) {
        for (BeanPropertyDefinition property : description.findProperties()) {
            if (propertyName.equals(property.getName())) {
                return property;
            }
        }
        throw new IllegalArgumentException("Missing property: " + propertyName);
    }

    private static AnnotatedMethod roundTripThroughJavaSerialization(AnnotatedMethod method) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(method);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AnnotatedMethod) input.readObject();
        }
    }

    public static class NoArgumentFactoryBean {
        private final String value;

        private NoArgumentFactoryBean(String value) {
            this.value = value;
        }

        @JsonCreator
        public static NoArgumentFactoryBean create() {
            return new NoArgumentFactoryBean("created-without-arguments");
        }

        public String getValue() {
            return value;
        }
    }

    public static class OneArgumentFactoryBean {
        private final String value;

        private OneArgumentFactoryBean(String value) {
            this.value = value;
        }

        @JsonCreator
        public static OneArgumentFactoryBean create(String value) {
            return new OneArgumentFactoryBean(value);
        }

        public String getValue() {
            return value;
        }
    }

    public static class AccessorBean {
        private String value;

        public AccessorBean() {
        }

        public AccessorBean(String value) {
            this.value = value;
        }

        @JsonProperty("value")
        public String getValue() {
            return value;
        }

        @JsonProperty("value")
        public void setValue(String value) {
            this.value = value;
        }
    }
}
