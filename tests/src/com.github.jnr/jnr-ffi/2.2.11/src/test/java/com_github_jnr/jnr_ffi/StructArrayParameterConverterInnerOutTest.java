/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.annotations.Out;
import jnr.ffi.types.size_t;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructArrayParameterConverterInnerOutTest {
    public static class Word extends Struct {
        public final Signed32 value = new Signed32();

        public Word(Runtime runtime) {
            super(runtime);
        }
    }

    public interface StandardC {
        Pointer memset(@Out Word[] target, int value, @size_t long count);
    }

    @Test
    void reconstructsOutputStructsAfterANativeCall() {
        Word[] words = Struct.arrayOf(Runtime.getSystemRuntime(), Word.class, 3);
        Word original = words[0];
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        Pointer result = library.memset(words, 17, (long) Struct.size(words[0]) * words.length);

        assertThat(words[0]).isNotSameAs(original);
        assertThat(words).extracting(word -> word.value.get()).containsExactly(0x11111111, 0x11111111, 0x11111111);
        assertThat(result.address()).isEqualTo(Struct.getMemory(words[0]).address());
    }
}
