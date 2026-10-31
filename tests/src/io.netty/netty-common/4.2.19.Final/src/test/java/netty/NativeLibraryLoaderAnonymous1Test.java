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

public class NativeLibraryLoaderAnonymous1Test {
    @Test
    void helperReportsNativeLinkFailureForAnUnavailableLibrary() {
        String name = "netty_metadata_unavailable_helper_library";
        UnsatisfiedLinkError error = Assertions.assertThrows(UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(name, NativeLibraryLoaderAnonymous1Test.class.getClassLoader()));
        FileNotFoundException missing = Assertions.assertInstanceOf(FileNotFoundException.class, error.getCause());
        Assertions.assertTrue(missing.getSuppressed().length > 0);
        UnsatisfiedLinkError directLoad = Assertions.assertInstanceOf(UnsatisfiedLinkError.class, missing.getSuppressed()[0]);
        Assertions.assertTrue(directLoad.getSuppressed().length > 0);
        UnsatisfiedLinkError helperLoad = Assertions.assertInstanceOf(UnsatisfiedLinkError.class, directLoad.getSuppressed()[0]);
        Assertions.assertTrue(helperLoad.getMessage().contains(name), helperLoad::toString);
    }
}
