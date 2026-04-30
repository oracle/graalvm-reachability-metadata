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
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;

public class AnnotatedMethodTest {
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    @Test
    void invokesStaticMethodsViaCallVariants() throws Exception {
        AnnotatedMethod noArgMethod = staticMethod("staticGreeting", 0);
        AnnotatedMethod arrayArgMethod = staticMethod("staticPair", 2);
        AnnotatedMethod oneArgMethod = staticMethod("staticTag", 1);

        assertThat(noArgMethod.call()).isEqualTo("hello");
        assertThat(arrayArgMethod.call(new Object[] { "left", "right" })).isEqualTo("left:right");
        assertThat(oneArgMethod.call1("value")).isEqualTo("tag:value");
    }

    @Test
    void invokesInstanceMethodsWithTargetObjectsAndArguments() throws Exception {
        MethodTarget target = new MethodTarget("seed");
        AnnotatedMethod noArgMethod = instanceMethod("describe", NO_PARAMETERS);
        AnnotatedMethod argumentMethod = instanceMethod("combine", String.class, int.class);

        assertThat(noArgMethod.callOn(target)).isEqualTo("description:seed");
        assertThat(argumentMethod.callOnWith(target, "branch", 3)).isEqualTo("seed:branch:3");
    }

    @Test
    void getsAndSetsBeanValuesThroughAnnotatedMethods() {
        MethodTarget target = new MethodTarget("initial");
        AnnotatedMethod getter = instanceMethod("getName", NO_PARAMETERS);
        AnnotatedMethod setter = instanceMethod("setName", String.class);

        assertThat(getter.getValue(target)).isEqualTo("initial");
        setter.setValue(target, "updated");
        assertThat(getter.getValue(target)).isEqualTo("updated");
    }

    @Test
    void resolvesMethodAfterJavaSerializationRoundTrip() throws Exception {
        AnnotatedMethod original = instanceMethod("describe", NO_PARAMETERS);

        AnnotatedMethod restored = serializeAndDeserialize(original);

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.callOn(new MethodTarget("restored"))).isEqualTo("description:restored");
    }

    private static AnnotatedMethod instanceMethod(String name, Class<?>... parameterTypes) {
        AnnotatedMethod method = beanDescription().findMethod(name, parameterTypes);

        assertThat(method).isNotNull();
        return method;
    }

    private static AnnotatedMethod staticMethod(String name, int parameterCount) {
        for (AnnotatedMethod method : beanDescription().getClassInfo().getStaticMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new AssertionError("Missing static method " + name + " with " + parameterCount + " parameters");
    }

    private static BeanDescription beanDescription() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.getSerializationConfig().introspect(mapper.constructType(MethodTarget.class));
    }

    private static AnnotatedMethod serializeAndDeserialize(AnnotatedMethod method) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(method);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (AnnotatedMethod) objectInput.readObject();
        }
    }

    public static final class MethodTarget {
        private String name;

        public MethodTarget() {
            this("default");
        }

        MethodTarget(String name) {
            this.name = name;
        }

        public static String staticGreeting() {
            return "hello";
        }

        public static String staticPair(String left, String right) {
            return left + ":" + right;
        }

        public static String staticTag(String value) {
            return "tag:" + value;
        }

        public String describe() {
            return "description:" + name;
        }

        public String combine(String label, int count) {
            return name + ":" + label + ":" + count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
