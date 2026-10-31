/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.FileNotFoundException;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderTest {
    @Test
    void searchesSystemResourcesForAnUnavailableLibrary() {
        assertMissingLibrary(null, "netty_metadata_unavailable_system_library");
    }

    @Test
    void searchesApplicationResourcesForAnUnavailableLibrary() {
        assertMissingLibrary(NativeLibraryLoaderTest.class.getClassLoader(), "netty_metadata_unavailable_app_library");
    }

    private static void assertMissingLibrary(ClassLoader loader, String name) {
        UnsatisfiedLinkError error = Assertions.assertThrows(UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(name, loader));
        Assertions.assertTrue(error.getMessage().contains(name));
        FileNotFoundException missing = Assertions.assertInstanceOf(FileNotFoundException.class, error.getCause());
        Assertions.assertTrue(missing.getMessage().contains(name));
        Assertions.assertTrue(missing.getSuppressed().length > 0);
        Assertions.assertInstanceOf(UnsatisfiedLinkError.class, missing.getSuppressed()[0]);
    }
}
