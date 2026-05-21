/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.Test;

public class LinkedListImplTest {
    @Test
    public void growsIteratorArrayAndKeepsOpenIteratorsConsistentAfterRemoval() {
        LinkedListImpl<String> list = new LinkedListImpl<>();
        list.addTail("one");
        list.addTail("two");
        list.addTail("three");

        List<LinkedListIterator<String>> iterators = new ArrayList<>();
        try {
            for (int i = 0; i < 11; i++) {
                LinkedListIterator<String> iterator = list.iterator();
                assertThat(iterator.next()).isEqualTo("one");
                iterators.add(iterator);
            }

            assertThat(list.numIters()).isEqualTo(11);
            assertThat(list.poll()).isEqualTo("one");

            for (LinkedListIterator<String> iterator : iterators) {
                assertThat(iterator.hasNext()).isTrue();
                assertThat(iterator.next()).isEqualTo("two");
            }
        } finally {
            for (LinkedListIterator<String> iterator : iterators) {
                iterator.close();
            }
        }

        assertThat(list.numIters()).isZero();
        assertThat(list.size()).isEqualTo(2);
    }
}
