/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CollectionsInnerCheckedCollectionTest {
    @Test
    void addAllCopiesCompatibleElementsThroughTypedEmptyArray() {
        Collection checkedCollection = Collections.checkedCollection(new ArrayList(), String.class);

        boolean modified = checkedCollection.addAll(Arrays.asList("alpha", "bravo"));

        assertThat(modified).isTrue();
        assertThat(checkedCollection).containsExactly("alpha", "bravo");
    }

    @Test
    void addAllRejectsIncompatibleElementsBeforeMutatingBackingCollection() {
        Collection backingCollection = new ArrayList();
        Collection checkedCollection = Collections.checkedCollection(backingCollection, String.class);

        assertThatThrownBy(() -> checkedCollection.addAll(Arrays.asList("alpha", Integer.valueOf(1))))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("collection of type java.lang.String");
        assertThat(backingCollection).isEmpty();
    }
}
