/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.net.URL;
import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Library;
import org.eclipse.swt.internal.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LibraryTest {
    @TempDir
    Path libraryPath;

    @Test
    void isLoadableChecksThePackagedLibraryClassResource() {
        URL libraryClass = Platform.class.getClassLoader().getResource("org/eclipse/swt/internal/Library.class");

        boolean loadable = SWT.isLoadable();

        if (libraryClass != null && "jar".equals(libraryClass.getProtocol())) {
            assertThat(loadable).isEqualTo(isWindowsX8664());
        } else {
            assertThat(loadable).isTrue();
        }
    }

    @Test
    void loadLibraryAttemptsToExtractMissingMappedLibraryResourceFromClasspath() {
        String previousLibraryPath = System.getProperty("swt.library.path");
        String missingLibraryName = "forge-missing-swt-library";
        try {
            System.setProperty("swt.library.path", libraryPath.toString());

            UnsatisfiedLinkError error = catchThrowableOfType(
                    () -> Library.loadLibrary(missingLibraryName),
                    UnsatisfiedLinkError.class);

            assertThat(error)
                    .hasMessageContaining("Could not load SWT library")
                    .hasMessageContaining(missingLibraryName);
        } finally {
            restoreProperty("swt.library.path", previousLibraryPath);
        }
    }

    private static boolean isWindowsX8664() {
        String osName = System.getProperty("os.name", "");
        String osArch = System.getProperty("os.arch", "");
        return osName.startsWith("Win") && ("amd64".equals(osArch) || "x86_64".equals(osArch));
    }

    private static void restoreProperty(String propertyName, String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, propertyValue);
        }
    }
}
