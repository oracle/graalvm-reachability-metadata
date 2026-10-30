/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import java.util.Collections;

import jnr.ffi.CallingConvention;
import jnr.ffi.NativeType;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.provider.ParameterType;
import jnr.ffi.provider.ResultType;
import jnr.ffi.provider.jffi.FastLongMethodGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFastNumericMethodGeneratorTest {
    @Test
    void selectsIntegerAndPointerSignaturesForTheHostCallingConvention() {
        FastLongMethodGenerator generator = new FastLongMethodGenerator();
        Platform platform = Platform.getNativePlatform();
        boolean supportedHost = platform.getCPU() == Platform.CPU.X86_64
                && platform.getOS() != Platform.OS.WINDOWS;
        ResultType integerResult = resultType(long.class, NativeType.SLONGLONG);
        ParameterType[] integerParameters = {
                parameterType(long.class, NativeType.SLONGLONG),
                parameterType(int.class, NativeType.SINT)
        };
        ResultType pointerResult = resultType(Pointer.class, NativeType.ADDRESS);
        ParameterType[] pointerParameters = {
                parameterType(Pointer.class, NativeType.ADDRESS)
        };

        assertThat(generator.isSupported(integerResult, integerParameters, CallingConvention.DEFAULT))
                .isEqualTo(supportedHost);
        assertThat(generator.isSupported(pointerResult, pointerParameters, CallingConvention.DEFAULT))
                .isEqualTo(supportedHost);
        assertThat(generator.isSupported(resultType(void.class, NativeType.VOID),
                pointerParameters, CallingConvention.DEFAULT)).isEqualTo(supportedHost);
    }

    @Test
    void rejectsSignaturesThatNeedADifferentInvoker() {
        FastLongMethodGenerator generator = new FastLongMethodGenerator();
        ResultType integerResult = resultType(long.class, NativeType.SLONGLONG);
        ParameterType integerParameter = parameterType(long.class, NativeType.SLONGLONG);
        ParameterType[] tooManyParameters = {
                integerParameter, integerParameter, integerParameter, integerParameter,
                integerParameter, integerParameter, integerParameter
        };

        assertThat(generator.isSupported(integerResult, tooManyParameters, CallingConvention.DEFAULT)).isFalse();
        assertThat(generator.isSupported(integerResult, new ParameterType[]{integerParameter},
                CallingConvention.STDCALL)).isFalse();
        assertThat(generator.isSupported(integerResult,
                new ParameterType[]{parameterType(double.class, NativeType.DOUBLE)},
                CallingConvention.DEFAULT)).isFalse();
        assertThat(generator.isSupported(resultType(double.class, NativeType.DOUBLE),
                new ParameterType[]{integerParameter}, CallingConvention.DEFAULT)).isFalse();
    }

    private static ParameterType parameterType(Class<?> javaType, NativeType nativeType) {
        return new ParameterType(javaType, nativeType, Collections.emptyList(), null, null);
    }

    private static ResultType resultType(Class<?> javaType, NativeType nativeType) {
        return new ResultType(javaType, nativeType, Collections.emptyList(), null, null);
    }
}
