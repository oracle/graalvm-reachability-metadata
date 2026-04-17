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

    private static final AtomicReferenceFieldUpdater<StringArrayHolder, String[]> VALUES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(StringArrayHolder.class, String[].class, "values");
    private static final AtomicArray<StringArrayHolder, String> STRING_ARRAY =
            AtomicArray.create(VALUES_UPDATER, String.class);

    @Test
    void managesTypedStringArrays() {
        StringArrayHolder holder = new StringArrayHolder(new String[] { "initial" });

        STRING_ARRAY.clear(holder);

        assertThat(holder.values)
                .isInstanceOf(String[].class)
                .isEmpty();

        STRING_ARRAY.add(holder, "alpha");
        STRING_ARRAY.add(holder, "beta");

        assertThat(holder.values)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta");

        boolean removed = STRING_ARRAY.remove(holder, "alpha", false);

        assertThat(removed).isTrue();
        assertThat(holder.values)
                .isInstanceOf(String[].class)
                .containsExactly("beta");
    }

    private static final class StringArrayHolder {

        volatile String[] values;

        private StringArrayHolder(String[] values) {
            this.values = values;
        }
    }
}
