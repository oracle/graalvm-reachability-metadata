/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.DdemlUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DdemlUtil_DdemlExceptionTest {
    private static final int DMLERR_BUSY = 0x4001;

    private static String previousJnaLibraryPath;

    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void configureLoadableUser32Library() throws IOException {
        previousJnaLibraryPath = System.getProperty("jna.library.path");
        if (isWindows()) {
            return;
        }

        Path sharedLibrary = findLoadableSharedLibrary();
        Path aliasedUser32Library = temporaryDirectory.resolve(System.mapLibraryName("user32"));

        try {
            Files.createSymbolicLink(aliasedUser32Library, sharedLibrary);
        } catch (UnsupportedOperationException | IOException exception) {
            Files.copy(sharedLibrary, aliasedUser32Library, StandardCopyOption.REPLACE_EXISTING);
        }

        System.setProperty("jna.library.path", prependLibraryPath(temporaryDirectory));
    }

    @AfterAll
    static void restoreJnaLibraryPath() {
        if (previousJnaLibraryPath == null) {
            System.clearProperty("jna.library.path");
            return;
        }
        System.setProperty("jna.library.path", previousJnaLibraryPath);
    }

    @Test
    void createUsesDdemlErrorConstantNameInMessage() {
        DdemlUtil.DdemlException exception = DdemlUtil.DdemlException.create(DMLERR_BUSY);

        assertThat(exception.getErrorCode()).isEqualTo(DMLERR_BUSY);
        assertThat(exception)
                .hasMessageContaining("DMLERR_BUSY")
                .hasMessageContaining(String.format("0x%X", DMLERR_BUSY));
    }

    @Test
    void createLeavesUnknownErrorCodesUnnamed() {
        int unknownErrorCode = Integer.MAX_VALUE;

        DdemlUtil.DdemlException exception = DdemlUtil.DdemlException.create(unknownErrorCode);

        assertThat(exception.getErrorCode()).isEqualTo(unknownErrorCode);
        assertThat(exception)
                .hasMessage(String.format(" (Code: 0x%X)", unknownErrorCode));
    }

    private static Path findLoadableSharedLibrary() throws IOException {
        List<Path> searchRoots = isMac()
                ? List.of(
                        Path.of("/usr/lib"),
                        Path.of("/System/Library"),
                        Path.of("/opt/homebrew/lib"),
                        Path.of("/usr/local/lib")
                )
                : List.of(
                        Path.of("/lib"),
                        Path.of("/lib64"),
                        Path.of("/usr/lib"),
                        Path.of("/usr/lib64"),
                        Path.of("/lib/x86_64-linux-gnu"),
                        Path.of("/usr/lib/x86_64-linux-gnu")
                );
        List<String> candidateNames = isMac()
                ? List.of("libSystem.B.dylib", "libobjc.A.dylib", "libc.dylib")
                : List.of("libc.so.6", "libc.musl-x86_64.so.1", "libc.so", "libm.so.6", "libdl.so.2");

        for (Path searchRoot : searchRoots) {
            Path directCandidate = findDirectCandidate(searchRoot, candidateNames);
            if (directCandidate != null) {
                return directCandidate;
            }

            Path nestedCandidate = findNestedCandidate(searchRoot, candidateNames);
            if (nestedCandidate != null) {
                return nestedCandidate;
            }
        }

        throw new IOException("No loadable shared library was found to alias as user32");
    }

    private static Path findDirectCandidate(Path searchRoot, List<String> candidateNames) {
        for (String candidateName : candidateNames) {
            Path candidate = searchRoot.resolve(candidateName);
            if (Files.isRegularFile(candidate) || Files.isSymbolicLink(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Path findNestedCandidate(Path searchRoot, List<String> candidateNames) throws IOException {
        if (!Files.isDirectory(searchRoot)) {
            return null;
        }

        try (Stream<Path> children = Files.walk(searchRoot, 3)) {
            return children
                    .filter(path -> Files.isRegularFile(path) || Files.isSymbolicLink(path))
                    .filter(path -> candidateNames.contains(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String prependLibraryPath(Path libraryDirectory) {
        if (previousJnaLibraryPath == null || previousJnaLibraryPath.isBlank()) {
            return libraryDirectory.toString();
        }
        return libraryDirectory + File.pathSeparator + previousJnaLibraryPath;
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
