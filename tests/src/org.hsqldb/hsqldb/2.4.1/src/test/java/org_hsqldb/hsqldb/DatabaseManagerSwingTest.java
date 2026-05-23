/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import org.hsqldb.util.DatabaseManagerSwing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DatabaseManagerSwingTest {
    private static final String FRAME_TITLE = "DatabaseManagerSwingTest";

    @AfterEach
    void closeOpenWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof JFrame && FRAME_TITLE.equals(((JFrame) window).getTitle())) {
                    window.dispose();
                }
            }
        });
    }

    @Test
    @Timeout(30)
    void mainBuildsMenusWithTransferToolsDisabledWhenTransferClassesAreUnavailable() throws Exception {
        AtomicReference<JFrame> frameReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame(FRAME_TITLE);
            DatabaseManagerSwing databaseManager = new DatabaseManagerSwing(frame);

            databaseManager.main();
            frameReference.set(frame);
        });

        JFrame frame = frameReference.get();
        JMenu toolsMenu = findMenu(frame.getJMenuBar(), "Tools");

        assertThat(toolsMenu).isNotNull();
        assertThat(toolsMenu.isEnabled()).isFalse();
        assertThat(toolsMenu.getItem(0).getText()).isEqualTo("Dump");
        assertThat(toolsMenu.getItem(1).getText()).isEqualTo("Restore");
        assertThat(toolsMenu.getItem(2).getText()).isEqualTo("Transfer");
    }

    private static JMenu findMenu(JMenuBar menuBar, String text) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);

            if (menu != null && text.equals(menu.getText())) {
                return menu;
            }
        }

        return null;
    }
}
