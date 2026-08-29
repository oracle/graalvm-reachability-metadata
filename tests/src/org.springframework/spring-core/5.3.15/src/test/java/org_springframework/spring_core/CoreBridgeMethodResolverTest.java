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

/** Verifies resolution of compiler-generated generic bridge methods. */
public class CoreBridgeMethodResolverTest {
    @Test
    void resolvesBridgeToTypedMethod() {
        Method bridge = Arrays.stream(StringHandler.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow(AssertionError::new);

        Method bridged = BridgeMethodResolver.findBridgedMethod(bridge);

        assertThat(bridged.isBridge()).isFalse();
        assertThat(bridged.getParameterTypes()).containsExactly(String.class);
    }

    public interface Handler<T> {
        T handle(T value);
    }

    public static final class StringHandler implements Handler<String> {
        @Override
        public String handle(String value) {
            return value.toUpperCase();
        }
    }
}
