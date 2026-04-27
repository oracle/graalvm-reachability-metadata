/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentSkipListSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentSkipListSetTest {
    @Test
    void cloneCreatesIndependentOrderedSet() {
        ConcurrentSkipListSet original = new ConcurrentSkipListSet();
        original.add("delta");
        original.add("alpha");
        original.add("charlie");
        original.add("bravo");

        ConcurrentSkipListSet cloned = (ConcurrentSkipListSet) original.clone();

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.toArray()).containsExactly("alpha", "bravo", "charlie", "delta");

        assertThat(cloned.remove("bravo")).isTrue();
        assertThat(cloned.add("echo")).isTrue();
        assertThat(cloned.toArray()).containsExactly("alpha", "charlie", "delta", "echo");
        assertThat(original.toArray()).containsExactly("alpha", "bravo", "charlie", "delta");

        assertThat(original.pollFirst()).isEqualTo("alpha");
        assertThat(original.toArray()).containsExactly("bravo", "charlie", "delta");
        assertThat(cloned.toArray()).containsExactly("alpha", "charlie", "delta", "echo");
    }
}
