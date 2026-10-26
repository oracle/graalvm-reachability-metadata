/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.io.netty.util.internal.TypeParameterMatcher;
import org.junit.jupiter.api.Test;

public class TypeParameterMatcherTest {
    @Test
    void resolvesParameterizedArrayType() {
        TypeParameterMatcher matcher = TypeParameterMatcher.find(new ListArrayHolder(), Holder.class, "T");

        assertThat(matcher.match(new List<?>[0])).isTrue();
        assertThat(matcher.match(new Object[0])).isFalse();
    }

    private abstract static class Holder<T> {
    }

    private static final class ListArrayHolder extends Holder<List<String>[]> {
    }
}
