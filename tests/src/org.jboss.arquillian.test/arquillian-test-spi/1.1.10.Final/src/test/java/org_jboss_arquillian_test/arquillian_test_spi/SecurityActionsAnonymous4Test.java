/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.jboss.arquillian.test.spi.TestClass;
import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous4Test {
    @Test
    void testClassFindsAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        TestClass testClass = new TestClass(ChildComponent.class);

        Method[] methods = testClass.getMethods(LifecycleHook.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("parentHook", "childHook");
        assertThat(invokeMethod(methods, "parentHook", new ChildComponent())).isEqualTo("parent");
        assertThat(invokeMethod(methods, "childHook", new ChildComponent())).isEqualTo("child");
    }

    private static Object invokeMethod(Method[] methods, String methodName, Object target) throws Exception {
        Method matchingMethod = findMethod(methods, methodName);
        assertThat(matchingMethod.canAccess(target)).isTrue();
        return matchingMethod.invoke(target);
    }

    private static Method findMethod(Method[] methods, String methodName) {
        return Arrays.stream(methods)
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + methodName));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface LifecycleHook {
    }

    private static class ParentComponent {
        @LifecycleHook
        private String parentHook() {
            return "parent";
        }

        @SuppressWarnings("unused")
        private String ignoredParentHook() {
            return "ignored parent";
        }
    }

    private static final class ChildComponent extends ParentComponent {
        @LifecycleHook
        private String childHook() {
            return "child";
        }

        @SuppressWarnings("unused")
        private String ignoredChildHook() {
            return "ignored child";
        }
    }
}
