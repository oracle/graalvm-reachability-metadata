/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.util.ArrayList;
import java.util.List;

import io.netty.util.internal.TypeParameterMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypeParameterMatcherTest {
    @Test
    void resolvesGenericArrayTypeParametersToTheirArrayClass() {
        TypeParameterMatcher matcher = TypeParameterMatcher.find(
                new StringListArrayHandler(), ArrayAwareHandler.class, "T");

        List<?>[] matchingArray = new List<?>[] {new ArrayList<String>()};

        Assertions.assertSame(TypeParameterMatcher.get(List[].class), matcher);
        Assertions.assertTrue(matcher.match(matchingArray));
        Assertions.assertFalse(matcher.match(new ArrayList<>()));
    }

    private abstract static class ArrayAwareHandler<T> {
    }

    private static final class StringListArrayHandler extends ArrayAwareHandler<List<String>[]> {
    }
}
