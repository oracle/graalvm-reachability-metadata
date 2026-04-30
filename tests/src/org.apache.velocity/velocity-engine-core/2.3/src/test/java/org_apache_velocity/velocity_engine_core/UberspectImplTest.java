/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void returnsIteratorFromDuckTypedIteratorMethod() {
        UberspectImpl uberspect = new UberspectImpl();
        Iterator<?> iterator = uberspect.getIterator(
                new IteratorOnlySequence("alpha", "beta", "gamma"),
                new Info("duck-typed-iterator", 1, 1));

        assertThat(iterator).isNotNull();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.next()).isEqualTo("beta");
        assertThat(iterator.next()).isEqualTo("gamma");
        assertThat(iterator.hasNext()).isFalse();
    }

    public static class IteratorOnlySequence {
        private final List<String> values;

        IteratorOnlySequence(String... values) {
            this.values = Arrays.asList(values);
        }

        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
