/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotatedMethodTest {

    @Test
    void annotatedMethodInvokesStaticMethodsInstanceAccessorsAndReadResolve() throws Exception {
        Method staticMethod = MethodTarget.class.getDeclaredMethod("decorate", String.class);
        AnnotatedMethod annotatedStatic = new AnnotatedMethod(staticMethod, new AnnotationMap(), null);

        assertThat(annotatedStatic.call(new Object[]{"bean" })).isEqualTo("decorated-bean");
        assertThat(annotatedStatic.call1("value")).isEqualTo("decorated-value");

        Method zeroArgStatic = MethodTarget.class.getDeclaredMethod("constant");
        AnnotatedMethod zeroArg = new AnnotatedMethod(zeroArgStatic, new AnnotationMap(), null);
        assertThat(zeroArg.call()).isEqualTo("constant");

        Method getter = MethodTarget.class.getDeclaredMethod("getValue");
        Method setter = MethodTarget.class.getDeclaredMethod("setValue", String.class);
        AnnotatedMethod annotatedGetter = new AnnotatedMethod(getter, new AnnotationMap(), null);
        AnnotatedMethod annotatedSetter = new AnnotatedMethod(setter, new AnnotationMap(), null);
        MethodTarget target = new MethodTarget();

        annotatedSetter.setValue(target, "updated");
        assertThat(annotatedGetter.getValue(target)).isEqualTo("updated");

        AnnotatedMethod restored = reserialize(annotatedGetter);
        assertThat(restored.getName()).isEqualTo("getValue");
        assertThat(restored.getValue(target)).isEqualTo("updated");
    }

    private static <T> T reserialize(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T restored = (T) input.readObject();
            return restored;
        }
    }

    public static class MethodTarget {

        private String value;

        public static String constant() {
            return "constant";
        }

        public static String decorate(String input) {
            return "decorated-" + input;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
