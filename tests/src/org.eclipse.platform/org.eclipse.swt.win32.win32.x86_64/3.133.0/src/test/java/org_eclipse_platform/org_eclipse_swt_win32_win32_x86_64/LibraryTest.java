/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class LibraryTest {
    @Test
    void determinesWhetherSwtCanLoad() {
        assertThatCode(SWT::isLoadable).doesNotThrowAnyException();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void extractsAndLoadsSwtLibraryWhenCreatingWindow() throws IOException {
        Path libraryDirectory = Files.createTempDirectory("swt-native-libraries-");
        String originalLibraryPath = System.getProperty("swt.library.path");
        System.setProperty("swt.library.path", libraryDirectory.toString());
        try {
            Display display = new Display();
            Shell shell = new Shell(display);
            try {
                shell.setSize(200, 200);
                shell.open();

                assertThat(shell.isVisible()).isTrue();
                try (Stream<Path> files = Files.list(libraryDirectory)) {
                    assertThat(files).isNotEmpty();
                }
            } finally {
                shell.dispose();
                display.dispose();
            }
        } finally {
            if (originalLibraryPath == null) {
                System.clearProperty("swt.library.path");
            } else {
                System.setProperty("swt.library.path", originalLibraryPath);
            }
        }
    }
}
