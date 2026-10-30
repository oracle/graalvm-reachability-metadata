/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.mapper.ToNativeContext;
import jnr.ffi.mapper.ToNativeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationTypeMapperTest {
    public static final class Magnitude {
        private final int value;

        public Magnitude(int value) {
            this.value = value;
        }

        @ToNativeConverter.ToNative(nativeType = Integer.class)
        public static Integer toNative(Magnitude value, ToNativeContext context) {
            return value.value;
        }
    }

    public interface StandardC {
        int abs(Magnitude value);
    }

    @Test
    void discoversAnAnnotatedArgumentConverterWithoutAnExplicitMapper() {
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        assertThat(library.abs(new Magnitude(-67))).isEqualTo(67);
    }
}
