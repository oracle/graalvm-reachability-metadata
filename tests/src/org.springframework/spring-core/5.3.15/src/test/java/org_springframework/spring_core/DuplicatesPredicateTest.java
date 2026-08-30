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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.DuplicatesPredicate;

/** Verifies duplicate filtering when a generic override contributes a bridge method. */
public class DuplicatesPredicateTest {
    @Test
    void filtersDuplicateBridgeSignature() throws NoSuchMethodException {
        Method bridge = Arrays.stream(StringProcessor.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow(AssertionError::new);
        Method parentMethod = GenericProcessor.class.getDeclaredMethod("process", Object.class);
        DuplicatesPredicate predicate = new DuplicatesPredicate(List.of(bridge, parentMethod));

        assertThat(predicate.evaluate(bridge)).isTrue();
        assertThat(predicate.evaluate(parentMethod)).isFalse();
    }

    public abstract static class GenericProcessor<T> {
        public abstract T process(T value);
    }

    public static final class StringProcessor extends GenericProcessor<String> {
        @Override
        public String process(String value) {
            return value.toUpperCase();
        }
    }
}
