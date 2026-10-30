/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.internal.NativeLibraryLoader;

public class NativeLibraryLoaderTest {
    @Test
    void reportsWhenNoneOfTheRequestedNativeLibrariesExist() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> NativeLibraryLoader.loadFirstAvailable(null, "metadata_test_nonexistent_library"));
        assertTrue(failure.getMessage().contains("metadata_test_nonexistent_library"));
        assertTrue(failure.getSuppressed().length > 0);
    }
}
