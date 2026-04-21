/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.list.SetUniqueList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SetUniqueListTest {

    @Test
    void subListCopiesCustomBackingSetThroughItsCopyConstructor() {
        ConstructorTrackingSet.resetCopyConstructorCalls();

        List<String> backingList = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        ConstructorTrackingSet<String> backingSet = new ConstructorTrackingSet<>();
        backingSet.addAll(backingList);
        SetUniqueList<String> uniqueList = new TestSetUniqueList<>(backingList, backingSet);

        List<String> subList = uniqueList.subList(1, 3);

        assertThat(ConstructorTrackingSet.copyConstructorCalls()).isEqualTo(1);
        assertThat(subList).containsExactly("beta", "gamma");
        assertThatThrownBy(() -> subList.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class TestSetUniqueList<E> extends SetUniqueList<E> {

        private TestSetUniqueList(List<E> list, Set<E> set) {
            super(list, set);
        }
    }

    public static final class ConstructorTrackingSet<E> extends LinkedHashSet<E> {

        private static final AtomicInteger COPY_CONSTRUCTOR_CALLS = new AtomicInteger();

        public ConstructorTrackingSet() {
        }

        public ConstructorTrackingSet(ConstructorTrackingSet<E> other) {
            super(other);
            COPY_CONSTRUCTOR_CALLS.incrementAndGet();
        }

        private static void resetCopyConstructorCalls() {
            COPY_CONSTRUCTOR_CALLS.set(0);
        }

        private static int copyConstructorCalls() {
            return COPY_CONSTRUCTOR_CALLS.get();
        }
    }
}
