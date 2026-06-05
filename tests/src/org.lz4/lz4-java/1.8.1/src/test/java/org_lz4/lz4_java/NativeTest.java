/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import java.nio.charset.StandardCharsets;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.util.Native;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NativeTest {

    @Test
    void shouldLoadBundledNativeLibraryAndRoundTripPayload() {
        String resourceName = bundledLibraryResourceName();
        Assumptions.assumeTrue(resourceName != null && Native.class.getResource(resourceName) != null,
                "Expected a bundled native lz4-java library for this platform");

        LZ4Factory factory = LZ4Factory.nativeInstance();
        byte[] payload = ("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "cccccccccccccccccccccccccccccccc"
                + "lz4 native library loading coverage")
                .getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[factory.fastCompressor().maxCompressedLength(payload.length)];

        int compressedLength = factory.fastCompressor().compress(payload, 0, payload.length, compressed, 0, compressed.length);

        byte[] restored = new byte[payload.length];
        int decompressedLength = factory.safeDecompressor().decompress(compressed, 0, compressedLength, restored, 0);

        assertThat(factory).isSameAs(LZ4Factory.nativeInstance());
        assertThat(factory.toString()).contains("JNI");
        assertThat(Native.isLoaded()).isTrue();
        assertThat(decompressedLength).isEqualTo(payload.length);
        assertThat(restored).isEqualTo(payload);
    }

    private static String bundledLibraryResourceName() {
        String osName = System.getProperty("os.name");
        String osDirectory;
        String libraryExtension;
        if (osName.contains("Linux")) {
            osDirectory = "linux";
            libraryExtension = "so";
        } else if (osName.contains("Mac")) {
            osDirectory = "darwin";
            libraryExtension = "dylib";
        } else if (osName.contains("Windows")) {
            osDirectory = "win32";
            libraryExtension = "so";
        } else {
            return null;
        }
        String architecture = System.getProperty("os.arch");
        return "/net/jpountz/util/" + osDirectory + "/" + architecture + "/liblz4-java." + libraryExtension;
    }
}
