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

    public static class CompositeStruct extends Struct {
        public class Counter extends Signed32 {
        }

        public final Signed16[] words = array(new Signed16[2]);
        public final Counter[] counters = (Counter[]) array((Member[]) new Counter[2]);
        public final NumberStruct[] numbers = array(new NumberStruct[2]);
        public final NumberStruct nested = inner(NumberStruct.class);

        public CompositeStruct(Runtime runtime) {
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

    @Test
    void constructsMemberAndNestedStructArraysAndDescribesTheirValues() {
        CompositeStruct composite = new CompositeStruct(Runtime.getSystemRuntime());
        composite.words[0].set(5);
        composite.words[1].set(8);
        composite.counters[0].set(11);
        composite.counters[1].set(12);
        composite.numbers[0].value.set(13);
        composite.numbers[1].value.set(21);
        composite.nested.value.set(34);

        assertThat(composite.words).extracting(Struct.Signed16::get).containsExactly((short) 5, (short) 8);
        assertThat(composite.counters).extracting(Struct.Signed32::get).containsExactly(11, 12);
        assertThat(composite.numbers).extracting(number -> number.value.get()).containsExactly(13, 21);
        assertThat(composite.nested.value.get()).isEqualTo(34);
        assertThat(composite.toString()).contains("words =", "counters =", "numbers =", "nested =");
    }
}
