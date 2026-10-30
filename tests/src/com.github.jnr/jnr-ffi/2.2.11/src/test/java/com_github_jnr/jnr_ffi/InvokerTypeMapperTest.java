/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.types.size_t;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokerTypeMapperTest {
    public interface StandardC {
        @size_t
        long strlen(CharSequence text);
    }

    @Test
    void convertsJavaTextToANativeCharacterSequence() {
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        assertThat(library.strlen("native text")).isEqualTo(11);
        assertThat(library.strlen(new StringBuilder("ffi"))).isEqualTo(3);
        assertThat(library.strlen("")).isZero();
    }
}
