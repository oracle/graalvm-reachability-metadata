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

/** Verifies generic declaration lookup while resolving a bridge method. */
public class BridgeMethodResolverTest {
    @Test
    void resolvesBridgeAgainstGenericParentDeclaration() {
        Method bridge = Arrays.stream(StringProcessor.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow(AssertionError::new);

        Method bridged = BridgeMethodResolver.findBridgedMethod(bridge);

        assertThat(bridged.isBridge()).isFalse();
        assertThat(bridged.getParameterTypes()).containsExactly(String.class);
    }

    public abstract static class GenericProcessor<T> {
        public abstract T process(T value);
    }

    public static final class StringProcessor extends GenericProcessor<String> {
        public Integer process(Integer value) {
            return value + 1;
        }

        @Override
        public String process(String value) {
            return value.toUpperCase();
        }
    }
}
