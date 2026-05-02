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
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

public class AnnotationsScannerTest {

    @Test
    void scansInterfaceMethodsWhenResolvingMethodAnnotationHierarchy() throws NoSuchMethodException {
        Method method = InterfaceMethodImplementation.class.getMethod("handle", String.class);

        MergedAnnotation<InterfaceMethodMarker> annotation = MergedAnnotations
                .from(method, SearchStrategy.TYPE_HIERARCHY)
                .get(InterfaceMethodMarker.class);

        assertThat(annotation.isPresent()).isTrue();
        assertThat(annotation.getString("value")).isEqualTo("interface-method");
    }

    private interface InterfaceMethodContract {

        @InterfaceMethodMarker("interface-method")
        String handle(String value);
    }

    private static final class InterfaceMethodImplementation implements InterfaceMethodContract {

        @Override
        public String handle(String value) {
            return value;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface InterfaceMethodMarker {

        String value();
    }
}
