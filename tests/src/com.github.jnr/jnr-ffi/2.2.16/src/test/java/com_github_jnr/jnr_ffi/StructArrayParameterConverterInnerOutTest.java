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

import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.annotations.Out;
import jnr.ffi.mapper.ToNativeContext;
import jnr.ffi.mapper.ToNativeConverter;
import jnr.ffi.provider.converters.StructArrayParameterConverter;
import jnr.ffi.util.AnnotationProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructArrayParameterConverterInnerOutTest {
    public static class Value extends Struct {
        public final Signed32 number = new Signed32();

        public Value(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void reloadsAnOutputStructArrayFromContiguousMemory() {
        Runtime runtime = Runtime.getSystemRuntime();
        Value[] values = Struct.arrayOf(runtime, Value.class, 2);
        ToNativeContext context = new Context(runtime);
        ToNativeConverter<Struct[], Pointer> converter =
                StructArrayParameterConverter.getInstance(context, Value.class);
        Pointer memory = converter.toNative(values, context);
        memory.putInt(0, 7);
        memory.putInt(Struct.size(values[0]), 11);

        @SuppressWarnings("unchecked")
        ToNativeConverter.PostInvocation<Struct[], Pointer> postInvocation =
                (ToNativeConverter.PostInvocation<Struct[], Pointer>) converter;
        postInvocation.postInvoke(values, memory, context);

        assertThat(values).extracting(value -> ((Value) value).number.get()).containsExactly(7, 11);
    }

    private static final class Context implements ToNativeContext {
        private final Runtime runtime;
        private final Out out = AnnotationProxy.newProxy(Out.class).getProxedAnnotation();

        private Context(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Collection<Annotation> getAnnotations() {
            return Collections.<Annotation>singletonList(out);
        }

        @Override
        public Runtime getRuntime() {
            return runtime;
        }
    }
}
