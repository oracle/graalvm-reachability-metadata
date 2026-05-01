/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.LazyList;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyListTest {
    @Test
    void createsTypedEmptyArrayWhenListIsNull() {
        Object array = LazyList.toArray(null, String.class);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).isEmpty();
    }

    @Test
    void createsPrimitiveArrayFromListBackedLazyList() {
        Object lazyList = LazyList.add(null, Integer.valueOf(3));
        lazyList = LazyList.add(lazyList, Integer.valueOf(5));

        Object array = LazyList.toArray(lazyList, Integer.TYPE);

        assertThat(array).isInstanceOf(int[].class);
        assertThat((int[]) array).containsExactly(3, 5);
    }

    @Test
    void createsTypedObjectArrayFromListBackedLazyList() {
        Object lazyList = LazyList.add(null, "alpha");
        lazyList = LazyList.add(lazyList, "beta");

        Object array = LazyList.toArray(lazyList, String.class);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("alpha", "beta");
    }

    @Test
    void wrapsSingleLazyListValueInTypedArray() {
        Object array = LazyList.toArray("solo", String.class);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("solo");
    }

    @Test
    void addToArrayCreatesTypedArrayWhenSourceArrayIsNull() {
        Object[] array = LazyList.addToArray(null, "first", null);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("first");
    }

    @Test
    void addToArrayExtendsExistingArrayWithTheSameComponentType() {
        String[] source = {"first", "second"};

        Object[] array = LazyList.addToArray(source, "third", null);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("first", "second", "third");
    }

    @Test
    void removeFromArrayRemovesMatchingItemAndKeepsComponentType() {
        String[] source = {"first", "second", "third"};

        Object[] array = LazyList.removeFromArray(source, "second");

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("first", "third");
    }
}
