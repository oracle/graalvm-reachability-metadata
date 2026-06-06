/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_gsonfire.gson_fire;

import io.gsonfire.annotations.PreSerialize;
import io.gsonfire.util.reflection.MethodInspector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMethodInspectorTest {
    @Test
    void discoversAnnotatedMethodsDeclaredOnTargetClass() {
        MethodInspector inspector = new MethodInspector();

        Collection<Method> annotatedMethods = inspector.getAnnotatedMembers(
                LifecycleCallbacks.class,
                PreSerialize.class);

        assertThat(annotatedMethods)
                .extracting(Method::getName)
                .containsExactly("prepareForSerialization");
    }

    private static final class LifecycleCallbacks {
        @PreSerialize
        private void prepareForSerialization() {
        }

        private void helperMethodWithoutAnnotation() {
        }
    }
}
