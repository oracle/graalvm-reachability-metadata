/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.DuplicatesPredicate;

/** Verifies bridge-aware method filtering using the declaring class's bytecode. */
public class DuplicatesPredicateTest {
    @Test
    void filtersDuplicateSignaturesInGenericHierarchy() throws Exception {
        List<Method> methods = new ArrayList<>();
        Method bridge = null;
        for (Method method : Child.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                bridge = method;
                methods.add(method);
            }
        }
        Method parentMethod = Parent.class.getMethod("value");
        methods.add(parentMethod);
        DuplicatesPredicate predicate = new DuplicatesPredicate(methods);

        assertThat(bridge).isNotNull();
        assertThat(predicate.evaluate(bridge)).isTrue();
        assertThat(predicate.evaluate(parentMethod)).isFalse();
        assertThat(predicate.evaluate(Child.class.getMethod("value"))).isTrue();
    }

    public static class Parent<T> {
        public T value() {
            return null;
        }
    }

    public static class Child extends Parent<String> {
        @Override
        public String value() {
            return "child";
        }
    }
}
