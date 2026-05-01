/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.NavigableSet;
import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentSkipListSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConcurrentSkipListSetTest {
    @Test
    void cloneCreatesIndependentSetWithSameSortedContents() {
        ConcurrentSkipListSet original = new ConcurrentSkipListSet();
        original.add("bravo");
        original.add("alpha");
        original.add("charlie");

        ConcurrentSkipListSet clone = (ConcurrentSkipListSet) original.clone();

        assertThat(clone).containsExactly("alpha", "bravo", "charlie");
        assertThat(clone.first()).isEqualTo("alpha");
        assertThat(clone.last()).isEqualTo("charlie");

        original.remove("alpha");
        clone.add("delta");

        assertThat(original).containsExactly("bravo", "charlie");
        assertThat(original).doesNotContain("delta");
        assertThat(clone).containsExactly("alpha", "bravo", "charlie", "delta");
    }

    @Test
    void rangeViewsAreBackedAndRespectInclusiveBounds() {
        ConcurrentSkipListSet set = new ConcurrentSkipListSet();
        set.add(Integer.valueOf(1));
        set.add(Integer.valueOf(2));
        set.add(Integer.valueOf(3));
        set.add(Integer.valueOf(4));
        set.add(Integer.valueOf(5));

        NavigableSet middle = set.subSet(Integer.valueOf(2), true, Integer.valueOf(4), false);

        assertThat(middle).containsExactly(Integer.valueOf(2), Integer.valueOf(3));
        assertThat(middle.lower(Integer.valueOf(3))).isEqualTo(Integer.valueOf(2));
        assertThat(middle.ceiling(Integer.valueOf(3))).isEqualTo(Integer.valueOf(3));

        assertThat(middle.remove(Integer.valueOf(3))).isTrue();
        assertThat(set).containsExactly(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(4), Integer.valueOf(5));
        assertThatThrownBy(() -> middle.add(Integer.valueOf(4))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descendingViewAndPollOperationsMirrorSortedSetSemantics() {
        ConcurrentSkipListSet set = new ConcurrentSkipListSet();
        set.add("alpha");
        set.add("bravo");
        set.add("charlie");

        NavigableSet descending = set.descendingSet();

        assertThat(descending).containsExactly("charlie", "bravo", "alpha");
        assertThat(descending.pollFirst()).isEqualTo("charlie");
        assertThat(set).containsExactly("alpha", "bravo");
        assertThat(set.pollLast()).isEqualTo("bravo");
        assertThat(descending).containsExactly("alpha");
    }
}
