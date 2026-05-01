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
    void objectCopyOfRangeAllocatesArrayWithOriginalComponentType() {
        String[] names = new String[] {"alpha", "bravo", "charlie"};

        String[] copiedNames = (String[]) Arrays.copyOfRange(names, 1, 5);

        assertThat(copiedNames).isInstanceOf(String[].class);
        assertThat(copiedNames).containsExactly("bravo", "charlie", null, null);
    }

    @Test
    void objectCopyOfRangeAllocatesArrayWithRequestedComponentType() {
        String[] names = new String[] {"alpha", "bravo", "charlie"};

        CharSequence[] copiedNames = (CharSequence[]) Arrays.copyOfRange(names, 0, 2, CharSequence[].class);

        assertThat(copiedNames).isInstanceOf(CharSequence[].class);
        assertThat(copiedNames).containsExactly("alpha", "bravo");
    }
}
