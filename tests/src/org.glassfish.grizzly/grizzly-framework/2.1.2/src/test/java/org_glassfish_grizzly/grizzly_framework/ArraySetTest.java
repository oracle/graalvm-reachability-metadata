/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.ArraySet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ArraySetTest {
    @Test
    void createsTypedEmptyArrayForElementClass() {
        ArraySet<String> set = new ArraySet<>(String.class);

        String[] values = set.obtainArrayCopy();

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).isEmpty();
    }

    @Test
    void varargsAddAllInitializesTypedBackingArray() {
        ArraySet<String> set = new ArraySet<>(String.class);

        boolean changed = set.addAll("alpha", "beta", "alpha");

        assertThat(changed).isTrue();
        assertThat(set.getArray())
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta");
    }

    @Test
    void collectionAddAllInitializesTypedBackingArray() {
        ArraySet<String> set = new ArraySet<>(String.class);
        List<String> values = Arrays.asList("one", "two", "one");

        boolean changed = set.addAll(values);

        assertThat(changed).isTrue();
        assertThat(set.getArray())
                .isInstanceOf(String[].class)
                .contains("one", "two");
    }

    @Test
    void retainAllRebuildsTypedBackingArrayWithKeptValues() {
        ArraySet<String> set = new ArraySet<>(String.class);
        set.addAll("alpha", "beta", "gamma");

        boolean changed = set.retainAll(Arrays.asList("beta", "gamma", "delta"));

        assertThat(changed).isTrue();
        assertThat(set.getArray())
                .isInstanceOf(String[].class)
                .containsExactly("beta", "gamma");
    }

    @Test
    void removeAllCollectionRebuildsTypedBackingArrayWithoutRemovedValues() {
        ArraySet<String> set = new ArraySet<>(String.class);
        set.addAll("alpha", "beta", "gamma");

        boolean changed = set.removeAll(Collections.singleton("beta"));

        assertThat(changed).isTrue();
        assertThat(set.getArray())
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "gamma");
    }
}
