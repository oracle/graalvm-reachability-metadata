/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;
import jnr.ffi.mapper.FromNativeContext;
import jnr.ffi.mapper.FromNativeConverter;
import jnr.ffi.mapper.ToNativeContext;
import jnr.ffi.mapper.ToNativeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationTypeMapperInnerAbstractReflectionConverterTest {
    public interface StandardC {
        NativeInteger abs(NativeInteger value);
    }

    public static final class NativeInteger {
        private final int value;

        private NativeInteger(int value) {
            this.value = value;
        }

        @ToNativeConverter.ToNative(nativeType = Integer.class)
        public static Integer toNative(NativeInteger value, ToNativeContext context) {
            return value.value;
        }

        @FromNativeConverter.FromNative(nativeType = Integer.class)
        public static NativeInteger fromNative(Integer value, FromNativeContext context) {
            return new NativeInteger(value);
        }
    }

    @Test
    void invokesAnnotatedConversionsAroundANativeFunction() {
        System.setProperty("jnr.ffi.asm.enabled", "false");
        StandardC standardC = LibraryLoader.create(StandardC.class)
                .load(Platform.getNativePlatform().getStandardCLibraryName());

        assertThat(standardC.abs(new NativeInteger(-23)).value).isEqualTo(23);
    }
}
