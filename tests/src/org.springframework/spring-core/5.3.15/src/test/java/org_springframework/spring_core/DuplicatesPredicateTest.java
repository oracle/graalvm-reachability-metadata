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
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.DuplicatesPredicate;
import org.springframework.core.ExceptionDepthComparator;

/** Verifies duplicate filtering when a generic override contributes a bridge method. */
public class DuplicatesPredicateTest {
    @Test
    void filtersDuplicateBridgeSignature() throws NoSuchMethodException {
        Method bridge = Arrays.stream(ExceptionDepthComparator.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow(AssertionError::new);
        Method comparatorMethod = Comparator.class.getMethod("compare", Object.class, Object.class);
        DuplicatesPredicate predicate = new DuplicatesPredicate(Arrays.asList(bridge, comparatorMethod));

        assertThat(predicate.evaluate(bridge)).isTrue();
        assertThat(predicate.evaluate(comparatorMethod)).isFalse();
    }
}
