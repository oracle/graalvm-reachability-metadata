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
    public static class NumberStruct extends Struct {
        public final Signed32 value = new Signed32();

        public NumberStruct(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void createsContiguousStructArrayAndComputesItsSize() {
        Runtime runtime = Runtime.getSystemRuntime();
        NumberStruct[] structs = Struct.arrayOf(runtime, NumberStruct.class, 2);
        structs[0].value.set(17);
        structs[1].value.set(29);

        assertThat(Struct.size(NumberStruct.class, runtime)).isEqualTo(Struct.size(structs[0]));
        assertThat(structs).extracting(struct -> struct.value.get()).containsExactly(17, 29);
        assertThat(Struct.getMemory(structs[1]).address() - Struct.getMemory(structs[0]).address())
                .isEqualTo(Struct.size(structs[0]));
    }
}
