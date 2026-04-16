/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v1.cachedstore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SoftSetFactoryTest {
    @Test
    void createSynchronousCleanupSoftSetDelegatesSetOperationsToTheProxyBackedSet() {
        Set<Object> softSet = SoftSetFactory.createSynchronousCleanupSoftSet();

        assertThat(softSet.add("alpha")).isTrue();
        assertThat(softSet.add("beta")).isTrue();
        assertThat(softSet.add("alpha")).isFalse();

        assertThat(softSet).contains("alpha", "beta");
        assertThat(iteratedElementsOf(softSet)).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(softSet.toArray()).containsExactlyInAnyOrder("alpha", "beta");

        assertThat(softSet.remove("alpha")).isTrue();
        assertThat(softSet).containsExactly("beta");

        softSet.clear();

        assertThat(softSet).isEmpty();
    }

    @Test
    void createSynchronousCleanupSoftSetReturnsIndependentSets() {
        Set<Object> firstSoftSet = SoftSetFactory.createSynchronousCleanupSoftSet();
        Set<Object> secondSoftSet = SoftSetFactory.createSynchronousCleanupSoftSet();

        firstSoftSet.add("first");
        secondSoftSet.add("second");

        assertThat(firstSoftSet).containsExactly("first");
        assertThat(secondSoftSet).containsExactly("second");
    }

    private static List<Object> iteratedElementsOf(Set<Object> softSet) {
        List<Object> elements = new ArrayList<>();
        Iterator<Object> iterator = softSet.iterator();
        while (iterator.hasNext()) {
            elements.add(iterator.next());
        }
        return elements;
    }
}
