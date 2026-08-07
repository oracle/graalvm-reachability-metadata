/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.ArrayList;
import java.util.List;

import io.netty.util.internal.TypeParameterMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeParameterMatcherTest {
    @Test
    public void resolvesGenericArrayTypeParametersToTheirArrayClass() {
        TypeParameterMatcher matcher = TypeParameterMatcher.find(
                new StringListArrayHandler(), ArrayAwareHandler.class, "T");

        List<?>[] matchingArray = new List<?>[] {new ArrayList<String>()};

        assertThat(matcher).isSameAs(TypeParameterMatcher.get(List[].class));
        assertThat(matcher.match(matchingArray)).isTrue();
        assertThat(matcher.match(new ArrayList<>())).isFalse();
    }

    private abstract static class ArrayAwareHandler<T> {
    }

    private static final class StringListArrayHandler extends ArrayAwareHandler<List<String>[]> {
    }
}
