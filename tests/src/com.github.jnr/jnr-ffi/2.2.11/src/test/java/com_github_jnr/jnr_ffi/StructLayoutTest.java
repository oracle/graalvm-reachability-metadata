/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.StructLayout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructLayoutTest {
    public static class VectorLayout extends StructLayout {
        public final Signed32[] values = array(new Signed32[3]);

        public VectorLayout(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void readsAndWritesAnArrayUsingTheComputedFieldOffsets() {
        Runtime runtime = Runtime.getSystemRuntime();
        VectorLayout layout = new VectorLayout(runtime);
        Pointer memory = Memory.allocateDirect(runtime, layout.size());
        layout.values[0].set(memory, 13);
        layout.values[1].set(memory, 21);
        layout.values[2].set(memory, 34);

        assertThat(layout.values[1].offset() - layout.values[0].offset()).isEqualTo(4);
        assertThat(layout.values[0].get(memory)).isEqualTo(13);
        assertThat(layout.values[1].get(memory)).isEqualTo(21);
        assertThat(layout.values[2].get(memory)).isEqualTo(34);
        assertThat(layout.toString()).startsWith("VectorLayout { ").endsWith("}\n");
    }
}
