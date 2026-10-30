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

/** Verifies annotation scanning across an implemented interface's methods. */
public class AnnotationsScannerTest {
    @Test
    void findsMethodAnnotationInInterfaceHierarchy() throws Exception {
        Method method = GreetingImplementation.class.getMethod("greet", String.class);

        MergedAnnotations annotations = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY);

        assertThat(annotations.get(Greeting.class).getString("value")).isEqualTo("interface");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Greeting {
        String value();
    }

    public interface GreetingContract {
        @Greeting("interface")
        String greet(String name);
    }

    public static final class GreetingImplementation implements GreetingContract {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
