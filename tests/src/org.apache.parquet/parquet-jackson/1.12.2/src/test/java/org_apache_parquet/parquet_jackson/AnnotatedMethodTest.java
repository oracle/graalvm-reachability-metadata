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
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotationMap;

public class AnnotatedMethodTest {
    @Test
    void invokesStaticMethodsThroughCallHelpers() throws Exception {
        final AnnotatedMethod noArgs = annotatedMethod("staticNoArgs");
        final AnnotatedMethod oneArg = annotatedMethod("staticOneArg", String.class);
        final AnnotatedMethod twoArgs =
                annotatedMethod("staticTwoArgs", String.class, String.class);

        assertThat(noArgs.call()).isEqualTo("static-value");
        assertThat(oneArg.call1("alpha")).isEqualTo("one:alpha");
        assertThat(twoArgs.call(new Object[] {"left", "right"})).isEqualTo("left:right");
    }

    @Test
    void invokesInstanceMethodsThroughCallHelpersAndPropertyAccessors() throws Exception {
        final Subject subject = new Subject("initial");
        final AnnotatedMethod instanceNoArgs = annotatedMethod("instanceNoArgs");
        final AnnotatedMethod join = annotatedMethod("join", String.class, String.class);
        final AnnotatedMethod getValue = annotatedMethod("getValue");
        final AnnotatedMethod setValue = annotatedMethod("setValue", String.class);

        assertThat(instanceNoArgs.callOn(subject)).isEqualTo("instance:initial");
        assertThat(join.callOnWith(subject, "left", "right")).isEqualTo("initial:left:right");

        setValue.setValue(subject, "updated");

        assertThat(getValue.getValue(subject)).isEqualTo("updated");
    }

    @Test
    void restoresSerializedMethod() throws Exception {
        final AnnotatedMethod original = annotatedMethod("staticNoArgs");

        final AnnotatedMethod restored = roundTrip(original);

        assertThat(restored.getName()).isEqualTo("staticNoArgs");
        assertThat(restored.call()).isEqualTo("static-value");
    }

    private static AnnotatedMethod annotatedMethod(String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Method method = Subject.class.getMethod(name, parameterTypes);
        final AnnotationMap[] parameterAnnotations = new AnnotationMap[parameterTypes.length];
        return new AnnotatedMethod(null, method, new AnnotationMap(), parameterAnnotations);
    }

    private static AnnotatedMethod roundTrip(AnnotatedMethod method) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(method);
        }

        final ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (AnnotatedMethod) input.readObject();
        }
    }

    public static final class Subject {
        private String value;

        public Subject(String value) {
            this.value = value;
        }

        public static String staticNoArgs() {
            return "static-value";
        }

        public static String staticOneArg(String value) {
            return "one:" + value;
        }

        public static String staticTwoArgs(String left, String right) {
            return left + ":" + right;
        }

        public String instanceNoArgs() {
            return "instance:" + value;
        }

        public String join(String left, String right) {
            return value + ":" + left + ":" + right;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
