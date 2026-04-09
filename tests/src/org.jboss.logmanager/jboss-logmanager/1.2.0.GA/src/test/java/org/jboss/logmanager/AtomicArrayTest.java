/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.junit.jupiter.api.Test;

class AtomicArrayTest {

    @Test
    void managesStringArraysUsingTypedArrayCopies() {
        final AtomicArray<StringArrayHolder, String> atomicArray = AtomicArray.create(
                AtomicReferenceFieldUpdater.newUpdater(StringArrayHolder.class, String[].class, "values"),
                String.class);
        final StringArrayHolder holder = new StringArrayHolder();

        atomicArray.clear(holder);
        atomicArray.add(holder, "alpha");
        atomicArray.add(holder, "beta");

        assertThat(holder.values).containsExactly("alpha", "beta");
        assertThat(atomicArray.remove(holder, "alpha", false)).isTrue();
        assertThat(holder.values).containsExactly("beta");
    }

    private static final class StringArrayHolder {

        private volatile String[] values = { "seed" };
    }
}
