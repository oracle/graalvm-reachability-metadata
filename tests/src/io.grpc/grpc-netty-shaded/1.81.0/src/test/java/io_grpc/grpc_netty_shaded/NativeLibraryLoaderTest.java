/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.internal.NativeLibraryLoader;

public class NativeLibraryLoaderTest {
    @Test
    void reportsEveryUnavailableCandidateWhenSearchingSystemResources() {
        String first = "metadata_test_absent_primary_transport";
        String second = "metadata_test_absent_secondary_transport";
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> NativeLibraryLoader.loadFirstAvailable(null, first, second));
        assertTrue(failure.getMessage().contains(first));
        assertTrue(failure.getMessage().contains(second));
        assertEquals(2, failure.getSuppressed().length);
        for (Throwable candidate : failure.getSuppressed()) {
            assertInstanceOf(UnsatisfiedLinkError.class, candidate);
        }
    }
}
