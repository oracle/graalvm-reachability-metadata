/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Ddeml;
import com.sun.jna.platform.win32.DdemlUtil.DdemlException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DdemlUtilInnerDdemlExceptionTest {
    private static final int BUSY_ERROR_CODE = Ddeml.DMLERR_BUSY;

    @TempDir
    Path tempDirectory;

    @Test
    void createBuildsMessageFromDdemlErrorConstants() throws IOException {
        makeUser32LoadableOnNonWindows();

        DdemlException exception = DdemlException.create(BUSY_ERROR_CODE);

        assertThat(exception.getErrorCode()).isEqualTo(BUSY_ERROR_CODE);
        assertThat(exception).hasMessageContaining("DMLERR_BUSY");
        assertThat(exception).hasMessageContaining("0x4001");
    }

    private void makeUser32LoadableOnNonWindows() throws IOException {
        if (Platform.isWindows()) {
            return;
        }

        Path user32Alias = tempDirectory.resolve(System.mapLibraryName("user32"));
        Path loadableLibrary = findLoadableNativeLibrary();

        createNativeLibraryAlias(loadableLibrary, user32Alias);
        NativeLibrary.addSearchPath("user32", tempDirectory.toString());
    }

    private static Path findLoadableNativeLibrary() throws IOException {
        String javaHome = System.getProperty("java.home", "");
        List<Path> candidates = List.of(
            Paths.get(javaHome, "lib", System.mapLibraryName("java")),
            Paths.get(javaHome, "lib", "server", System.mapLibraryName("jvm")),
            Paths.get("/lib/x86_64-linux-gnu/libc.so.6"),
            Paths.get("/usr/lib/x86_64-linux-gnu/libc.so.6"),
            Paths.get("/lib/aarch64-linux-gnu/libc.so.6"),
            Paths.get("/usr/lib/aarch64-linux-gnu/libc.so.6"),
            Paths.get("/lib64/libc.so.6"),
            Paths.get("/usr/lib64/libc.so.6")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Unable to find a native library for the user32 test alias");
    }

    private static void createNativeLibraryAlias(Path library, Path alias) throws IOException {
        try {
            Files.createSymbolicLink(alias, library);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Files.copy(library, alias);
        }
    }
}
