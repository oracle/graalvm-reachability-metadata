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

public class ArrayUtilsTest {
    @Test
    void createsGrowsAndCompactsTypedArrays() {
        String[] inserted = ArrayUtils.add((String[]) null, 0, "first");
        String[] appended = ArrayUtils.add(new String[] {"first"}, "second");
        String[] empty = ArrayUtils.nullToEmpty(null, String[].class);
        String[] allocated = ArrayUtils.newInstance(String.class, 2);
        String[] removed = ArrayUtils.remove(new String[] {"a", "b", "c"}, 1);
        String[] removedIndices = ArrayUtils.removeAll(new String[] {"a", "b", "c", "d"}, 1, 3);
        String[] removedOccurrences = ArrayUtils.removeAllOccurrences(
                new String[] {"a", "b", "a", "c"}, "a");

        assertThat(inserted).containsExactly("first");
        assertThat(appended).containsExactly("first", "second");
        assertThat(empty).isInstanceOf(String[].class).isEmpty();
        assertThat(allocated).isInstanceOf(String[].class).hasSize(2);
        assertThat(removed).containsExactly("a", "c");
        assertThat(removedIndices).containsExactly("a", "c");
        assertThat(removedOccurrences).containsExactly("b", "c");
    }
}
