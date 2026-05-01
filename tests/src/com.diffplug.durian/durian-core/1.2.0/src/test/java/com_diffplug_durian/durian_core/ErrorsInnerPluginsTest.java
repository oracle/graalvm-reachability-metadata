/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.Errors;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorsInnerPluginsTest {
    @Test
    public void defaultDialogPrintsErrorAndSchedulesSwingDialog() throws Exception {
        String previousHeadless = System.getProperty("java.awt.headless");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        RuntimeException error = new RuntimeException("dialog fallback path");

        try (PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setProperty("java.awt.headless", "true");
            System.setErr(replacementErr);

            Errors.Plugins.defaultDialog(error);
            SwingUtilities.invokeAndWait(() -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue());
        } finally {
            System.setErr(originalErr);
            restoreSystemProperty("java.awt.headless", previousHeadless);
        }

        String output = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains(RuntimeException.class.getName())
                .contains("dialog fallback path");
    }

    private static void restoreSystemProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
