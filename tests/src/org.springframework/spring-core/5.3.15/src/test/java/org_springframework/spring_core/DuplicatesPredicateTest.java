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

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.DuplicatesPredicate;

/** Verifies duplicate filtering for a compiler-generated bridge method. */
public class DuplicatesPredicateTest {
    @Test
    void rejectsUnnecessaryBridgeMethodAfterScanningDeclaringClass() throws Exception {
        Method bridgeMethod = findBridgeMethod();
        Method rootMethod = RootValue.class.getMethod("value");

        DuplicatesPredicate predicate = new DuplicatesPredicate(List.of(bridgeMethod, rootMethod));

        assertThat(predicate.evaluate(bridgeMethod)).isFalse();
        assertThat(predicate.evaluate(rootMethod)).isTrue();
    }

    private static Method findBridgeMethod() {
        for (Method method : StringValue.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                return method;
            }
        }
        throw new IllegalStateException("Expected compiler-generated bridge method");
    }

    private interface RootValue {
        Object value();
    }

    private interface GenericValue<T> extends RootValue {
        T value();
    }

    private static final class StringValue implements GenericValue<String> {
        @Override
        public String value() {
            return "value";
        }
    }
}
