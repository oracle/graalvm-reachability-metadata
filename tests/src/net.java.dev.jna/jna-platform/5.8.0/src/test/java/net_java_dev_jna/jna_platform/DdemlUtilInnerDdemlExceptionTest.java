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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        File cLibraryFile = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME).getFile();
        assertThat(cLibraryFile).isNotNull();

        createNativeLibraryAlias(cLibraryFile.toPath(), user32Alias);
        NativeLibrary.addSearchPath("user32", tempDirectory.toString());
    }

    private static void createNativeLibraryAlias(Path library, Path alias) throws IOException {
        try {
            Files.createSymbolicLink(alias, library);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Files.copy(library, alias);
        }
    }
}
