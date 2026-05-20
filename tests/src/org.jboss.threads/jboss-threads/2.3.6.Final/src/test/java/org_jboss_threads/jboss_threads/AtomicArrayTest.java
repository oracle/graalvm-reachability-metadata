/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_threads.jboss_threads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.threads.AtomicArray;
import org.junit.jupiter.api.Test;

public class AtomicArrayTest {
    @Test
    void createsEmptyArrayFromComponentType() {
        AtomicReferenceFieldUpdater<ArrayHolder, String[]> updater = AtomicReferenceFieldUpdater.newUpdater(
                ArrayHolder.class,
                String[].class,
                "values");
        AtomicArray<ArrayHolder, String> atomicArray = AtomicArray.create(updater, String.class);
        ArrayHolder holder = new ArrayHolder();

        atomicArray.clear(holder);
        atomicArray.add(holder, "first");
        atomicArray.add(holder, "second");
        boolean duplicateAdded = atomicArray.addIfAbsent(holder, "first", false);
        boolean thirdAdded = atomicArray.addIfAbsent(holder, "third", false);
        boolean removed = atomicArray.remove(holder, "second", false);

        assertThat(duplicateAdded).isFalse();
        assertThat(thirdAdded).isTrue();
        assertThat(removed).isTrue();
        assertThat(holder.values)
                .isInstanceOf(String[].class)
                .containsExactly("first", "third");
    }

    public static final class ArrayHolder {
        public volatile String[] values = {"initial"};
    }
}
