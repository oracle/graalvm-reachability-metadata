/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.mapper.FromNativeContext;
import jnr.ffi.mapper.FromNativeConverter;
import jnr.ffi.provider.converters.StructByReferenceFromNativeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructByReferenceFromNativeConverterTest {
    public static class Result extends Struct {
        public final Signed32 value = new Signed32();

        public Result(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void convertsNativeMemoryToAStructView() {
        Runtime runtime = Runtime.getSystemRuntime();
        Pointer memory = Memory.allocateDirect(runtime, 4);
        memory.putInt(0, 91);
        FromNativeContext context = new Context(runtime);
        FromNativeConverter<Struct, Pointer> converter =
                StructByReferenceFromNativeConverter.getInstance(Result.class, context);

        Result result = (Result) converter.fromNative(memory, context);

        assertThat(result.value.get()).isEqualTo(91);
    }

    private static final class Context implements FromNativeContext {
        private final Runtime runtime;

        private Context(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Collection<Annotation> getAnnotations() {
            return Collections.emptyList();
        }

        @Override
        public Runtime getRuntime() {
            return runtime;
        }
    }
}
