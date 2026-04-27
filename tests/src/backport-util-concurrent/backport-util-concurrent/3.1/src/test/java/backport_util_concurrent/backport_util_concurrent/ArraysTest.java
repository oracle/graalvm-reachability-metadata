/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArraysTest {
    @Test
    void copyOfRangePreservesTypedObjectArrayComponent() {
        String[] original = {"alpha", "bravo", "charlie"};

        Object[] copy = Arrays.copyOfRange(original, 1, 4);

        assertThat(copy).isInstanceOf(String[].class);
        assertThat((String[]) copy).containsExactly("bravo", "charlie", null);
    }
}
