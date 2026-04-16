/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.util.Native;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NativeTest {
    @Test
    void loadsBundledNativeLibraryFromResourcesAndSupportsCompressionRoundTrip(@TempDir Path tempDir) {
        String nativePathOverride = System.getProperty("ZstdNativePath");
        System.clearProperty("ZstdNativePath");
        try {
            Native.load(tempDir.toFile());
            assertThat(Native.isLoaded()).isTrue();

            byte[] input = "metadata-forge verifies zstd native loading".getBytes(StandardCharsets.UTF_8);
            byte[] compressed = Zstd.compress(input);
            byte[] decompressed = Zstd.decompress(compressed, input.length);

            assertThat(decompressed).isEqualTo(input);
        } finally {
            if (nativePathOverride == null) {
                System.clearProperty("ZstdNativePath");
            } else {
                System.setProperty("ZstdNativePath", nativePathOverride);
            }
        }
    }
}
