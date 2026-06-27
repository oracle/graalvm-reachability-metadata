/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.activemq.artemis.core.PriorityAware;
import org.apache.activemq.artemis.utils.collections.PriorityCollection;
import org.apache.activemq.artemis.utils.collections.ResettableIterator;
import org.junit.jupiter.api.Test;

public class PriorityCollectionTest {
    @Test
    public void createsPriorityIteratorArraysForOrderedAndResettableIteration() {
        PriorityCollection<PriorityElement> collection = new PriorityCollection<>(ArrayList::new);
        PriorityElement low = new PriorityElement("low", 1);
        PriorityElement high = new PriorityElement("high", 9);
        PriorityElement middle = new PriorityElement("middle", 5);
        PriorityElement secondHigh = new PriorityElement("second-high", 9);

        assertThat(collection.addAll(List.of(low, high, middle, secondHigh))).isTrue();

        assertThat(collection).hasSize(4);
        assertThat(collection.getPriorites()).containsExactlyInAnyOrder(1, 5, 9);
        assertThat(valuesFrom(collection.iterator())).containsExactly(high, secondHigh, middle, low);

        ResettableIterator<PriorityElement> resettableIterator = collection.resettableIterator();
        assertThat(valuesFrom(resettableIterator)).containsExactly(high, secondHigh, middle, low);

        resettableIterator.reset();
        assertThat(valuesFrom(resettableIterator)).containsExactly(high, secondHigh, middle, low);
    }

    private static List<PriorityElement> valuesFrom(Iterator<PriorityElement> iterator) {
        List<PriorityElement> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static final class PriorityElement implements PriorityAware {
        private final String name;
        private final int priority;

        private PriorityElement(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
