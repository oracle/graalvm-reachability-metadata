/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.hsqldb.jdbc.JDBCDriver;
import org.hsqldb.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ConnectionDialogTest implements Driver {
    private static final String DRIVER_CLASS_NAME = ConnectionDialogTest.class.getName();
    private static final String URL_PREFIX = "jdbc:connectiondialog:";
    private static final AtomicInteger CONSTRUCTOR_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger CONNECT_INVOCATIONS = new AtomicInteger();

    static {
        try {
            DriverManager.registerDriver(new ConnectionDialogTest());
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public ConnectionDialogTest() {
        CONSTRUCTOR_INVOCATIONS.incrementAndGet();
    }

    @AfterEach
    void closeDatabaseManagerWindows() {
        closeOpenWindows();
    }

    @Test
    @Timeout(30)
    void databaseManagerAutoConnectsThroughAwtConnectionDialog() throws Exception {
        String databaseName = "connection_dialog_" + Long.toUnsignedString(System.nanoTime());
        String realUrl = "jdbc:hsqldb:mem:" + databaseName;
        String dialogUrl = URL_PREFIX + realUrl;
        resetDriverInvocations();

        // `ConnectionDialog` is package-private, so exercise it through DatabaseManager's auto-connect API.
        DatabaseManager.main(new String[] {
            "--driver", DRIVER_CLASS_NAME,
            "--url", dialogUrl,
            "--user", "SA",
            "--password", "",
            "--noexit"
        });

        assertThat(CONSTRUCTOR_INVOCATIONS).hasValue(1);
        assertThat(CONNECT_INVOCATIONS).hasValue(1);
        assertThat(databaseManagerFrame()).isNotNull();
        try (Connection connection = DriverManager.getConnection(realUrl + ";ifexists=true", "SA", "")) {
            assertThat(connection.isValid(2)).isTrue();
        } finally {
            closeOpenWindows();
        }
    }

    private static Frame databaseManagerFrame() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Frame) {
                Frame frame = (Frame) window;

                if (isDatabaseManagerFrame(frame)) {
                    return frame;
                }
            }
        }

        return null;
    }

    private static boolean isDatabaseManagerFrame(Frame frame) {
        return "HSQL Database Manager".equals(frame.getTitle()) && frame.isDisplayable();
    }

    private static void closeOpenWindows() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Frame) {
                Frame frame = (Frame) window;

                if (isDatabaseManagerFrame(frame)) {
                    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                    frame.dispose();
                }
            }
        }
    }

    private static void resetDriverInvocations() {
        CONSTRUCTOR_INVOCATIONS.set(0);
        CONNECT_INVOCATIONS.set(0);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        CONNECT_INVOCATIONS.incrementAndGet();

        return new JDBCDriver().connect(url.substring(URL_PREFIX.length()), info);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
