/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.util.CollectionUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionUtilsTest {
    @Test
    void attemptCloneClonesArrayListUsingBuiltInPath() throws NoSuchMethodException {
        ArrayList<String> original = new ArrayList<>();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ArrayList.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesTreeSetUsingBuiltInPath() throws NoSuchMethodException {
        TreeSet<String> original = new TreeSet<>();
        original.add("beta");
        original.add("alpha");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(TreeSet.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesHashMapUsingBuiltInPath() throws NoSuchMethodException {
        HashMap<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(HashMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesTreeMapUsingBuiltInPath() throws NoSuchMethodException {
        TreeMap<String, Integer> original = new TreeMap<>();
        original.put("two", 2);
        original.put("one", 1);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(TreeMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }
}
