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
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/** Verifies annotation scanning across an interface method hierarchy. */
public class AnnotationsScannerTest {
    @Test
    void findsAnnotationDeclaredOnInterfaceMethod() throws Exception {
        Method method = HandlerImplementation.class.getMethod("handle", String.class);

        MergedAnnotations annotations = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY);

        assertThat(annotations.get(HandlerMethod.class).getString("value")).isEqualTo("interface");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface HandlerMethod {
        String value();
    }

    public interface Handler {
        @HandlerMethod("interface")
        String handle(String value);
    }

    public static final class HandlerImplementation implements Handler {
        @Override
        public String handle(String value) {
            return value;
        }
    }
}
