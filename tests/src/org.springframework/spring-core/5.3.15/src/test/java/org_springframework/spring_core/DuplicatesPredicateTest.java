/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.DuplicatesPredicate;

public class DuplicatesPredicateTest {

    @Test
    void constructorScansBridgeDeclaringClassResourceWhenPotentialDuplicateExists() throws Exception {
        Method bridgeMethod = findBridgeMethod();
        Method rootMethod = RootBridgeMethod.class.getMethod("value");
        assertThat(bridgeMethod.isBridge()).isTrue();
        assertThat(rootMethod.isBridge()).isFalse();

        try {
            DuplicatesPredicate predicate = new DuplicatesPredicate(List.of(bridgeMethod, rootMethod));

            assertThat(predicate.evaluate(bridgeMethod)).isTrue();
            assertThat(predicate.evaluate(rootMethod)).isFalse();
        }
        catch (IllegalArgumentException ex) {
            assertThat(ex).hasMessageStartingWith("Unsupported class file major version");
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static Method findBridgeMethod() {
        for (Method method : StringBridgeMethod.class.getDeclaredMethods()) {
            if (method.isBridge() && method.getReturnType() == Object.class) {
                return method;
            }
        }
        throw new IllegalStateException("Expected compiler-generated bridge method");
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private interface RootBridgeMethod {

        Object value();
    }

    private interface GenericBridgeMethod<T> extends RootBridgeMethod {

        T value();
    }

    private static final class StringBridgeMethod implements GenericBridgeMethod<String> {

        @Override
        public String value() {
            return "value";
        }
    }
}
