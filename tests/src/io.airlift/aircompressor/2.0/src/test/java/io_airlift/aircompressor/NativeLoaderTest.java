/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_airlift.aircompressor;

import io.airlift.compress.v2.internal.NativeLoader;
import io.airlift.compress.v2.internal.NativeSignature;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeLoaderTest {
    @Test
    void createsErrorMethodHandlesWhenNativeLibraryIsMissing() {
        NativeLoader.Symbols<BeginSignalSettingSymbols> symbols = NativeLoader.loadSymbols(
                "missing_native_loader_test",
                BeginSignalSettingSymbols.class,
                MethodHandles.lookup());

        assertThat(symbols.linkageError()).isPresent();
        assertThatThrownBy(() -> invokeBeginSignalSetting(symbols.symbols().beginSignalSetting()))
                .isInstanceOf(LinkageError.class)
                .hasMessageContaining("missing_native_loader_test native library not loaded");
    }

    private static void invokeBeginSignalSetting(MethodHandle beginSignalSetting) throws Throwable {
        beginSignalSetting.invokeExact();
    }

    private record BeginSignalSettingSymbols(
            @NativeSignature(
                    name = "JVM_begin_signal_setting",
                    returnType = void.class,
                    argumentTypes = {})
            MethodHandle beginSignalSetting) {}
}
