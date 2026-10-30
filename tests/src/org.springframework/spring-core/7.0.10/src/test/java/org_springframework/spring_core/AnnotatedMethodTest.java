/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedMethod;

/** Verifies parameter annotations inherited from both interfaces and superclasses. */
public class AnnotatedMethodTest {
    @Test
    void combinesInheritedParameterAnnotations() throws Exception {
        AnnotatedMethod method = new AnnotatedMethod(Handler.class.getMethod("handle", String.class));

        MethodParameter parameter = method.getMethodParameters()[0];

        assertThat(parameter.getParameterAnnotation(Input.class).value()).isEqualTo("interface");
        assertThat(parameter.getParameterAnnotation(Label.class).value()).isEqualTo("superclass");
        assertThat(parameter.getParameterType()).isEqualTo(String.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Input {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    public interface Contract {
        void handle(@Input("interface") String input);
    }

    public static class BaseHandler {
        public void handle(@Label("superclass") String input) { }
    }

    public static class Handler extends BaseHandler implements Contract {
        @Override
        public void handle(String input) { }
    }
}
