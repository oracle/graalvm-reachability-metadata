/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.hsqldb.jdbc.JDBCDriver;
import org.hsqldb.util.DatabaseManagerSwing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

public class ConnectionDialogCommonTest {
    @TempDir
    Path homeDirectory;

    private String previousUserHome;

    @AfterEach
    void restoreUserHomeAndCloseWindows() throws Exception {
        if (previousUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", previousUserHome);
        }

        disposeOpenWindows();
    }

    @Test
    @Timeout(30)
    @ResourceLock("user.home")
    void databaseManagerSwingHandlesRecentConnectionSettings() throws Exception {
        previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", homeDirectory.toString());

        DatabaseManagerSwing databaseManager = createDatabaseManager();
        Path settingsFile = homeDirectory.resolve("hsqlprefs.dat");

        try (Connection connection = openConnection("recent_store")) {
            connect(databaseManager, connection);
        }

        if (Files.exists(settingsFile)) {
            assertThat(settingsFile).isRegularFile();
            long storedSettingsSize = Files.size(settingsFile);

            assertThat(storedSettingsSize).isPositive();

            try (Connection connection = openConnection("recent_load")) {
                connect(databaseManager, connection);
            }

            assertThat(settingsFile).isRegularFile();
            assertThat(Files.size(settingsFile)).isPositive();
        } else {
            assertThat(settingsFile).doesNotExist();
        }
    }

    private static DatabaseManagerSwing createDatabaseManager() throws Exception {
        AtomicReference<DatabaseManagerSwing> databaseManager = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("ConnectionDialogCommonTest");
            DatabaseManagerSwing manager = new DatabaseManagerSwing(frame);

            manager.main();
            databaseManager.set(manager);
        });

        return databaseManager.get();
    }

    private static Connection openConnection(String databaseNamePrefix) throws Exception {
        String databaseName = databaseNamePrefix + "_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:mem:" + databaseName + ";shutdown=true";
        Properties properties = new Properties();

        properties.setProperty("user", "SA");
        properties.setProperty("password", "");

        Connection connection = new JDBCDriver().connect(url, properties);

        assertThat(connection).isNotNull();

        return connection;
    }

    private static void connect(DatabaseManagerSwing databaseManager, Connection connection) throws Exception {
        SwingUtilities.invokeAndWait(() -> databaseManager.connect(connection));
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void disposeOpenWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof JFrame && "ConnectionDialogCommonTest".equals(((JFrame) window).getTitle())) {
                    window.dispose();
                }
            }
        });
    }
}
