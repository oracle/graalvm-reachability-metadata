/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.Arrays;
import java.util.TreeSet;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSetConverterInnerReflectionsTest {
    @Test
    void restoresTreeSetUsingDefaultOptimizedAddAllDetection() {
        TreeSet<String> original = treeSetOf("bravo", "alpha", "charlie");

        TreeSet<String> restoredSet = roundTripTreeSet(new XStream(), original);

        assertThat(restoredSet.comparator()).isNull();
        assertThat(restoredSet).containsExactly("alpha", "bravo", "charlie");
    }

    @SuppressWarnings("unchecked")
    private static TreeSet<String> roundTripTreeSet(XStream xstream, TreeSet<String> original) {
        Object restored = xstream.fromXML(xstream.toXML(original));

        assertThat(restored).isInstanceOf(TreeSet.class);
        return (TreeSet<String>)restored;
    }

    private static TreeSet<String> treeSetOf(String... values) {
        TreeSet<String> set = new TreeSet<>();
        set.addAll(Arrays.asList(values));
        return set;
    }
}
