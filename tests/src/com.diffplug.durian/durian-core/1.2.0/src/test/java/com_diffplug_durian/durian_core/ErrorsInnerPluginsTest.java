/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import com.diffplug.common.base.Errors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ErrorsInnerPluginsTest {
    @Test
    @Timeout(10)
    void defaultDialogSchedulesAndRunsSwingDialogHandler() throws Exception {
        String previousHeadlessProperty = System.setProperty("java.awt.headless", "true");

        try {
            RuntimeException error = new RuntimeException("dialog failure");

            assertThatCode(() -> Errors.Plugins.defaultDialog(error)).doesNotThrowAnyException();
            waitForEventDispatchThread();
        } finally {
            restoreProperty("java.awt.headless", previousHeadlessProperty);
        }
    }

    private static void waitForEventDispatchThread() throws Exception {
        CountDownLatch dispatched = new CountDownLatch(1);
        SwingUtilities.invokeLater(dispatched::countDown);

        assertThat(dispatched.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
