/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dialog;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import junit.swingui.TestRunner;

public class AboutDialogTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void opensAboutDialogFromJUnitMenu() throws Exception {
        ExposedTestRunner runner = new ExposedTestRunner();
        JMenu menu = runner.newJUnitMenu();
        JMenuItem aboutItem = menu.getItem(0);
        CountDownLatch clickCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> clickFailure = new AtomicReference<>();

        SwingUtilities.invokeLater(() -> {
            try {
                aboutItem.doClick();
            } catch (Throwable throwable) {
                clickFailure.set(throwable);
            } finally {
                clickCompleted.countDown();
            }
        });

        Dialog aboutDialog = findVisibleAboutDialog();
        disposeOnEventDispatchThread(aboutDialog);

        assertThat(clickCompleted.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(clickFailure.get()).isNull();
        assertThat(aboutDialog.getTitle()).isEqualTo("About");
    }

    private static Dialog findVisibleAboutDialog() throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Dialog dialog = findVisibleAboutDialogOnce();
            if (dialog != null) {
                return dialog;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for the JUnit About dialog");
    }

    private static Dialog findVisibleAboutDialogOnce() throws Exception {
        AtomicReference<Dialog> dialog = new AtomicReference<>();
        runOnEventDispatchThread(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof Dialog) {
                    Dialog candidate = (Dialog) window;
                    if (candidate.isVisible() && "About".equals(candidate.getTitle())) {
                        dialog.set(candidate);
                        return;
                    }
                }
            }
        });
        return dialog.get();
    }

    private static void disposeOnEventDispatchThread(Dialog dialog) throws Exception {
        runOnEventDispatchThread(dialog::dispose);
    }

    private static void runOnEventDispatchThread(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    private static class ExposedTestRunner extends TestRunner {
        JMenu newJUnitMenu() {
            return createJUnitMenu();
        }
    }
}
