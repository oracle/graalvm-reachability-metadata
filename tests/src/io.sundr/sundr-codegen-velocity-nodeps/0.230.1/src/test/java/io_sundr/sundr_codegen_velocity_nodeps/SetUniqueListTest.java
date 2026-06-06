/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.list.SetUniqueList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class SetUniqueListTest {

    @Test
    public void subListCreatesUniqueListBackedBySameSetImplementation() {
        List source = new ArrayList(Arrays.asList("alpha", "beta", "gamma"));
        LinkedSetUniqueList uniqueList = new LinkedSetUniqueList(source);

        List subList = uniqueList.subList(0, 2);

        assertThat(subList).isInstanceOf(SetUniqueList.class);
        assertThat(uniqueList.createdSetClass).isEqualTo(LinkedHashSet.class);
        assertThat(subList).containsExactly("alpha", "beta");
        assertThat(subList.add("alpha")).isFalse();
        assertThat(subList.add("delta")).isTrue();
        assertThat(subList).containsExactly("alpha", "beta", "delta");
    }

    @Test
    public void decorateRemovesDuplicatesFromExistingList() {
        List source = new ArrayList(Arrays.asList("alpha", "beta", "alpha", "gamma"));

        SetUniqueList uniqueList = SetUniqueList.decorate(source);

        assertThat(uniqueList).containsExactly("alpha", "beta", "gamma");
        assertThat(source).containsExactly("alpha", "beta", "gamma");
        assertThat(uniqueList.add("beta")).isFalse();
        assertThat(uniqueList.add("delta")).isTrue();
        assertThat(uniqueList).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(uniqueList.asSet()).containsExactlyInAnyOrder("alpha", "beta", "gamma", "delta");
    }

    private static final class LinkedSetUniqueList extends SetUniqueList {

        private Class<?> createdSetClass;

        private LinkedSetUniqueList(List list) {
            super(list, new LinkedHashSet(list));
        }

        @Override
        protected Set createSetBasedOnList(Set set, List list) {
            Set createdSet = super.createSetBasedOnList(set, list);
            createdSetClass = createdSet.getClass();
            return createdSet;
        }
    }
}
