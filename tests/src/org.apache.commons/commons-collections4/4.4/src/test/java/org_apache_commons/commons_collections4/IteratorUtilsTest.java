/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IteratorUtilsTest {

    @Test
    void createsTypedArrayFromIteratorContents() {
        String[] values = IteratorUtils.toArray(List.of("alpha", "beta", "gamma").iterator(), String.class);

        assertThat(values)
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void usesPublicIteratorMethodWhenObjectIsNotIterable() {
        Iterator<?> iterator = IteratorUtils.getIterator(new IteratorMethodBackedValues(List.of("alpha", "beta", "gamma")));

        assertThat(IteratorUtils.toList(iterator))
                .isEqualTo(List.of("alpha", "beta", "gamma"));
    }

    public static final class IteratorMethodBackedValues {

        private final List<String> values;

        public IteratorMethodBackedValues(List<String> values) {
            this.values = values;
        }

        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
