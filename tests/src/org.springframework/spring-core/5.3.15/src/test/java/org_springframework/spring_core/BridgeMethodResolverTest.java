/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.BridgeMethodResolver;

public class BridgeMethodResolverTest {

    @Test
    void overloadedGenericHierarchyResolvesBridgeMethodThroughGenericDeclaration() {
        Method bridgeMethod = findBridgeMethod(StringStore.class, "store");

        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(bridgeMethod);

        assertThat(bridgedMethod.getName()).isEqualTo("store");
        assertThat(bridgedMethod.getDeclaringClass()).isEqualTo(StringStore.class);
        assertThat(bridgedMethod.getParameterTypes()).containsExactly(String.class);
    }

    private static Method findBridgeMethod(Class<?> declaringClass, String methodName) {
        return Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(Method::isBridge)
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected bridge method named " + methodName));
    }

    public static class GenericStore<T> {

        public T store(T value) {
            return value;
        }
    }

    public static class StringStore extends GenericStore<String> {

        @Override
        public String store(String value) {
            return "stored " + value;
        }

        public Number store(Number value) {
            return value;
        }
    }
}
