/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.Errors;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorsInnerPluginsTest {
    @Test
    public void defaultDialogPrintsErrorAndSchedulesSwingDialog() throws Exception {
        String previousHeadless = System.getProperty("java.awt.headless");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        RuntimeException error = new RuntimeException("dialog fallback path");

        Timer closeDialogs = new Timer(50, event -> disposeShowingWindows());
        closeDialogs.setRepeats(true);

        try (PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            enableAwtWhenDisplayIsAvailable();
            System.setErr(replacementErr);
            closeDialogs.start();

            Errors.Plugins.defaultDialog(error);
            SwingUtilities.invokeAndWait(() -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue());
        } finally {
            closeDialogs.stop();
            disposeShowingWindows();
            System.setErr(originalErr);
            restoreSystemProperty("java.awt.headless", previousHeadless);
        }

        String output = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains(RuntimeException.class.getName())
                .contains("dialog fallback path");
    }

    private static void enableAwtWhenDisplayIsAvailable() {
        if (System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null) {
            System.setProperty("java.awt.headless", "false");
        }
    }

    private static void disposeShowingWindows() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                window.dispose();
            }
        }
    }

    private static void restoreSystemProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
