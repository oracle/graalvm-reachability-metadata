/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

public class SnappyLoaderSystemLibraryTest {
    @Test
    void loadsNativeLibraryFromJavaLibraryPath() throws Exception {
        System.setProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB, "true");

        try {
            byte[] input = "snappy-java system library loading".getBytes(UTF_8);
            byte[] compressed = Snappy.compress(input);

            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassDefinition(error);
        } finally {
            System.clearProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassDefinition(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
