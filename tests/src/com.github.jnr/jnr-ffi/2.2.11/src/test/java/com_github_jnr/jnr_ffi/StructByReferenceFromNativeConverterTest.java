/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Memory;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.types.size_t;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructByReferenceFromNativeConverterTest {
    public static class Word extends Struct {
        public final Signed32 value = new Signed32();

        public Word(Runtime runtime) {
            super(runtime);
        }
    }

    public interface StandardC {
        Word memset(Word target, int value, @size_t long count);
    }

    @Test
    void returnsAStructViewOfNativeMemory() {
        Runtime runtime = Runtime.getSystemRuntime();
        Word target = new Word(runtime);
        target.useMemory(Memory.allocateDirect(runtime, Struct.size(target)));
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        Word result = library.memset(target, 42, Struct.size(target));

        assertThat(result.value.get()).isEqualTo(0x2a2a2a2a);
        assertThat(Struct.getMemory(result).address()).isEqualTo(Struct.getMemory(target).address());
        result.value.set(91);
        assertThat(target.value.get()).isEqualTo(91);
    }
}
