/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ArrayUtilsTest {
    @Test
    void preservesComponentTypesAcrossArrayTransformations() {
        String[] inserted = ArrayUtils.add(new String[] {"a", "c"}, 1, "b");
        String[] grown = ArrayUtils.add(new String[] {"a"}, "b");
        String[] createdFromNull = ArrayUtils.add((String[]) null, "first");
        String[] allocated = ArrayUtils.newInstance(String.class, 3);
        String[] empty = ArrayUtils.nullToEmpty(null, String[].class);
        String[] removed = ArrayUtils.remove(new String[] {"a", "b", "c"}, 1);
        String[] removedByIndex =
                ArrayUtils.removeAll(new String[] {"a", "b", "c", "d"}, 1, 3);
        String[] removedOccurrences = ArrayUtils.removeAllOccurrences(
                new String[] {"keep", "drop", "drop"}, "drop");

        assertThat(inserted).containsExactly("a", "b", "c");
        assertThat(grown).containsExactly("a", "b");
        assertThat(createdFromNull).containsExactly("first");
        assertThat(allocated).hasSize(3).containsOnlyNulls();
        assertThat(empty).isEmpty();
        assertThat(removed).containsExactly("a", "c");
        assertThat(removedByIndex).containsExactly("a", "c");
        assertThat(removedOccurrences).containsExactly("keep");
    }
}
