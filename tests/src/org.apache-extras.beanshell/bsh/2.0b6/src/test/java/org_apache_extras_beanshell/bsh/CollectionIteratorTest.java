/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.collection.CollectionIterator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionIteratorTest {

    @Test
    public void iteratesOverNonCollectionIterable() {
        CollectionIterator iterator = new CollectionIterator(new NamedIterable(Arrays.asList("alpha", "beta", "gamma")));

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("beta");
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("gamma");
        assertThat(iterator.hasNext()).isFalse();
    }

    public static class NamedIterable implements Iterable<String> {
        private final List<String> values;

        public NamedIterable(List<String> values) {
            this.values = values;
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
