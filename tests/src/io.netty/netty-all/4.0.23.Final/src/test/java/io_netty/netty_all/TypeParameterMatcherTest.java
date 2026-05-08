/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.TypeParameterMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeParameterMatcherTest {
    @Test
    void findResolvesParameterizedArrayTypeParameter() {
        TypeParameterMatcher matcher = TypeParameterMatcher.find(new ListArrayHolder(), Holder.class, "T");

        List<?>[] matchingArray = new List<?>[0];

        assertThat(matcher.match(matchingArray)).isTrue();
        assertThat(matcher.match(new Object[0])).isFalse();
    }

    private abstract static class Holder<T> {
    }

    private static final class ListArrayHolder extends Holder<List<String>[]> {
    }
}
