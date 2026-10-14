/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;
import jnr.ffi.mapper.DataConverter;
import jnr.ffi.mapper.DefaultTypeMapper;
import jnr.ffi.mapper.FromNativeContext;
import jnr.ffi.mapper.ToNativeContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConverterMetaDataTest {
    public interface StandardC {
        ConvertedInteger abs(ConvertedInteger value);
    }

    public static final class ConvertedInteger {
        private final int value;

        private ConvertedInteger(int value) {
            this.value = value;
        }
    }

    public static final class IntegerConverter implements DataConverter<ConvertedInteger, Integer> {
        @Override
        public Integer toNative(ConvertedInteger value, ToNativeContext context) {
            return value.value;
        }

        @Override
        public ConvertedInteger fromNative(Integer value, FromNativeContext context) {
            return new ConvertedInteger(value);
        }

        @Override
        public Class<Integer> nativeType() {
            return Integer.class;
        }
    }

    @Test
    void appliesARegisteredConverterToArgumentsAndResults() {
        System.setProperty("jnr.ffi.asm.enabled", "false");
        DefaultTypeMapper mapper = new DefaultTypeMapper();
        mapper.put(ConvertedInteger.class, new IntegerConverter());
        StandardC standardC = LibraryLoader.create(StandardC.class)
                .mapper(mapper)
                .load(Platform.getNativePlatform().getStandardCLibraryName());

        assertThat(standardC.abs(new ConvertedInteger(-31)).value).isEqualTo(31);
    }
}
