/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotatedMethodTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void invokesStaticFactoryMethodsThroughAnnotatedMethod() throws Exception {
        BeanDescription beanDescription = MAPPER.getDeserializationConfig()
                .introspectForCreation(MAPPER.constructType(FactoryTarget.class));

        AnnotatedMethod noArgumentFactory = findFactoryMethod(beanDescription, "empty");
        assertThat(noArgumentFactory.call()).isEqualTo(new FactoryTarget("empty"));

        AnnotatedMethod stringFactory = findFactoryMethod(beanDescription, "fromString", String.class);
        assertThat(stringFactory.call(new Object[] {"array"})).isEqualTo(new FactoryTarget("array"));
        assertThat(stringFactory.call1("single")).isEqualTo(new FactoryTarget("single"));
    }

    @Test
    void invokesInstanceMethodsThroughAnnotatedMethod() throws Exception {
        MethodTarget target = new MethodTarget("initial");
        BeanDescription beanDescription = MAPPER.getSerializationConfig()
                .introspect(MAPPER.constructType(MethodTarget.class));

        AnnotatedMethod getter = findMemberMethod(beanDescription, "getName");
        assertThat(getter.callOn(target)).isEqualTo("initial");
        assertThat(getter.getValue(target)).isEqualTo("initial");

        AnnotatedMethod combiner = findMemberMethod(beanDescription, "combine", String.class, String.class);
        assertThat(combiner.callOnWith(target, "first", "second")).isEqualTo("first:second");

        AnnotatedMethod setter = findMemberMethod(beanDescription, "setName", String.class);
        setter.setValue(target, "updated");
        assertThat(target.getName()).isEqualTo("updated");
    }

    @Test
    void exposesUnderlyingMethodMetadata() throws Exception {
        BeanDescription beanDescription = MAPPER.getSerializationConfig()
                .introspect(MAPPER.constructType(MethodTarget.class));
        AnnotatedMethod getter = findMemberMethod(beanDescription, "getName");

        assertThat(getter.getAnnotated()).isSameAs(getter.getMember());
        assertThat(getter.getDeclaringClass()).isEqualTo(MethodTarget.class);
        assertThat(getter.getRawReturnType()).isEqualTo(String.class);
        assertThat(getter.getRawParameterTypes()).isEmpty();
        assertThat(getter.getFullName()).endsWith("#getName()");
        assertThat(getter.callOn(new MethodTarget("resolved"))).isEqualTo("resolved");
    }

    private static AnnotatedMethod findFactoryMethod(BeanDescription beanDescription, String name,
            Class<?>... parameterTypes) {
        return beanDescription.getFactoryMethods().stream()
                .filter(method -> method.getName().equals(name))
                .filter(method -> Arrays.equals(method.getRawParameterTypes(), parameterTypes))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing factory method: " + name));
    }

    private static AnnotatedMethod findMemberMethod(BeanDescription beanDescription, String name,
            Class<?>... parameterTypes) {
        AnnotatedMethod method = beanDescription.findMethod(name, parameterTypes);
        assertThat(method).as("member method %s", name).isNotNull();
        return method;
    }

    public static final class FactoryTarget {
        private final String value;

        private FactoryTarget(String value) {
            this.value = value;
        }

        @JsonCreator
        public static FactoryTarget empty() {
            return new FactoryTarget("empty");
        }

        public static FactoryTarget fromString(String value) {
            return new FactoryTarget(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FactoryTarget)) {
                return false;
            }
            FactoryTarget that = (FactoryTarget) o;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static final class MethodTarget {
        private String name;

        public MethodTarget(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String combine(String first, String second) {
            return first + ":" + second;
        }
    }
}
