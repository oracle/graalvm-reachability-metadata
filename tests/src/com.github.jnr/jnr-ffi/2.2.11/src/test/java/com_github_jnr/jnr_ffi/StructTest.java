/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructTest {
    public static class Entry extends Struct {
        public final Signed32 key = new Signed32();
        public final Signed32 value = new Signed32();

        public Entry(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void createsIndependentViewsOfAContiguousStructArray() {
        Entry[] entries = Struct.arrayOf(Runtime.getSystemRuntime(), Entry.class, 3);
        entries[0].key.set(10);
        entries[1].key.set(20);
        entries[2].key.set(30);
        entries[1].value.set(75);

        assertThat(entries).extracting(entry -> entry.key.get()).containsExactly(10, 20, 30);
        assertThat(Struct.getMemory(entries[1]).address() - Struct.getMemory(entries[0]).address())
                .isEqualTo(Struct.size(entries[0]));
        assertThat(Struct.getMemory(entries[2]).address() - Struct.getMemory(entries[1]).address())
                .isEqualTo(Struct.size(entries[1]));
        assertThat(Struct.getMemory(entries[1]).getInt(entries[1].value.offset())).isEqualTo(75);
    }
}
