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

import static java.lang.invoke.MethodHandles.lookup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeLoaderTest {
    @Test
    void createsErrorMethodHandlesWhenNativeLibraryCannotBeLoaded() {
        NativeLoader.Symbols<NativeLoaderMethodHandles> symbols = NativeLoader.loadSymbols(
                "aircompressor_missing_test_library",
                NativeLoaderMethodHandles.class,
                lookup());

        assertThat(symbols.linkageError()).isPresent();
        assertThat(symbols.symbols().answer()).isNotNull();
        assertThatThrownBy(() -> {
            int ignored = (int) symbols.symbols().answer().invokeExact();
            assertThat(ignored).isZero();
        })
                .isInstanceOf(LinkageError.class)
                .hasMessageContaining("aircompressor_missing_test_library native library not loaded");
    }
}

record NativeLoaderMethodHandles(
        @NativeSignature(name = "aircompressor_missing_test_symbol", returnType = int.class, argumentTypes = {})
        MethodHandle answer) {}
