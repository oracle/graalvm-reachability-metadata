/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CollectionsInnerCheckedCollectionTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void addAllUsesTypedArrayWhenCopyingCompatibleElements() {
        Collection backingCollection = new ArrayList();
        Collection checkedCollection = Collections.checkedCollection(backingCollection, String.class);

        boolean changed = checkedCollection.addAll(Arrays.asList("alpha", "beta"));

        assertThat(changed).isTrue();
        assertThat(checkedCollection).containsExactly("alpha", "beta");
        assertThat(backingCollection).containsExactly("alpha", "beta");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void addAllRejectsElementsOutsideTheCheckedTypeBeforeMutatingBackingCollection() {
        Collection backingCollection = new ArrayList();
        Collection checkedCollection = Collections.checkedCollection(backingCollection, String.class);

        assertThatThrownBy(() -> checkedCollection.addAll(Arrays.asList("accepted", Integer.valueOf(1))))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("collection of type java.lang.String");
        assertThat(backingCollection).isEmpty();
    }
}
