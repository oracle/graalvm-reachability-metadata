/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.protocols.JDBC_PING;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBCPINGTest {
    private static final AtomicBoolean INITIALIZATION_DRIVER_LOADED = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_LINE_DRIVER_LOADED = new AtomicBoolean();

    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    @Test
    void protocolStackInitializationLoadsConfiguredJdbcDriver() throws Exception {
        ConfiguredJDBCPING jdbcPing = new ConfiguredJDBCPING()
                .withConnectionDriver(InitializationDriver.class.getName());

        try (JChannel ignored = new JChannel(new SHARED_LOOPBACK(), jdbcPing)) {
            assertThat(INITIALIZATION_DRIVER_LOADED).isTrue();
        }
    }

    @Test
    void commandLineToolLoadsConfiguredJdbcDriverClass() throws Exception {
        PrintStream originalError = System.err;
        PrintStream silentError = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        try {
            System.setErr(silentError);
            JDBC_PING.main(new String[] {
                    "-driver", CommandLineDriver.class.getName(),
                    "-conn", "jdbc:jgroups-test:unavailable",
                    "-user", "user",
                    "-pwd", "",
                    "-cluster", "JDBCPINGTest"
            });
        } finally {
            System.setErr(originalError);
            silentError.close();
        }

        assertThat(COMMAND_LINE_DRIVER_LOADED).isTrue();
    }

    public static class ConfiguredJDBCPING extends JDBC_PING {
        public ConfiguredJDBCPING withConnectionDriver(String driverClassName) {
            connection_url = "jdbc:jgroups-test:unused";
            connection_username = "user";
            connection_password = "";
            connection_driver = driverClassName;
            initialize_sql = "";
            register_shutdown_hook = false;
            return this;
        }
    }

    public static class InitializationDriver extends LoadOnlyDriver {
        static {
            INITIALIZATION_DRIVER_LOADED.set(true);
        }
    }

    public static class CommandLineDriver extends LoadOnlyDriver {
        static {
            COMMAND_LINE_DRIVER_LOADED.set(true);
        }
    }

    public abstract static class LoadOnlyDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return false;
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
            throw new SQLFeatureNotSupportedException("parent logger is not available");
        }
    }
}
