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
import org.springframework.util.comparator.ComparableComparator;

/** Verifies duplicate filtering for a compiler-generated bridge method. */
public class DuplicatesPredicateTest {
    @Test
    void scansBridgeMethodClassAndFiltersDuplicateSignature() throws Exception {
        Method bridgeMethod = Arrays.stream(ComparableComparator.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow(AssertionError::new);
        Method interfaceMethod = Comparator.class.getMethod("compare", Object.class, Object.class);

        DuplicatesPredicate predicate =
                new DuplicatesPredicate(Arrays.asList(bridgeMethod, interfaceMethod));

        assertThat(predicate.evaluate(bridgeMethod)).isTrue();
        assertThat(predicate.evaluate(interfaceMethod)).isFalse();
    }
}
