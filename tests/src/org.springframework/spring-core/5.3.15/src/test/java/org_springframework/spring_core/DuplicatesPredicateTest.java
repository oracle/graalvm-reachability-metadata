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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.DuplicatesPredicate;
import org.springframework.util.LinkedCaseInsensitiveMap;

/** Verifies CGLIB duplicate filtering for a compiler-generated bridge method. */
public class DuplicatesPredicateTest {
    @Test
    void filtersDuplicateBridgeAndGenericDeclaration() throws NoSuchMethodException {
        Method bridge = Arrays.stream(LinkedCaseInsensitiveMap.class.getDeclaredMethods())
                .filter(method -> method.isBridge() && method.getName().equals("put"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        Method declaration = Map.class.getMethod("put", Object.class, Object.class);
        List<Method> candidates = Arrays.asList(bridge, declaration);
        DuplicatesPredicate predicate = new DuplicatesPredicate(candidates);

        long acceptedMethods = candidates.stream().filter(predicate::evaluate).count();

        assertThat(acceptedMethods).isEqualTo(1);
    }
}
