/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

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
    void createsFieldArrayAndDescribesLayout() {
        VectorLayout layout = new VectorLayout(Runtime.getSystemRuntime());

        assertThat(layout.values).hasSize(3);
        assertThat(layout.values[1].offset()).isGreaterThan(layout.values[0].offset());
        assertThat(layout.toString()).contains("VectorLayout", "values");
    }
}
