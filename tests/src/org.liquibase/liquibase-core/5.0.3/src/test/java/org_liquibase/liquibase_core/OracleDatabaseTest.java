/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.OracleDatabase;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleDatabaseTest {
    private static final String ORACLE_PROXY_URL = "jdbc:oracle:thin:proxy_user/@localhost:1521/service";

    @Test
    void setConnectionConfiguresOracleProxySessionAndRemarksReporting() throws Exception {
        String h2Url = "jdbc:h2:mem:oracleDatabaseTest;DB_CLOSE_DELAY=-1";
        try (Connection realConnection = DriverManager.getConnection(h2Url)) {
            OracleCompatibleConnection oracleConnection = new OracleCompatibleConnection(
                    (org.h2.jdbc.JdbcConnection) realConnection
            );
            try {
                OracleDatabase database = new OracleDatabase();
                database.setConnection(new OracleProxyJdbcConnection(oracleConnection));

                assertThat(oracleConnection.getProxyType()).isEqualTo(1);
                assertThat(oracleConnection.getProxyUserName()).isEqualTo("proxy_user");
                assertThat(oracleConnection.isProxySession()).isTrue();
                assertThat(oracleConnection.isRemarksReporting()).isTrue();
                assertThat(database.getConnection().getURL()).isEqualTo(ORACLE_PROXY_URL);
            } finally {
                oracleConnection.close();
            }
        }
    }

    public static final class OracleProxyJdbcConnection extends JdbcConnection {
        public OracleProxyJdbcConnection(Connection connection) {
            super(connection);
        }

        @Override
        public String getURL() {
            return ORACLE_PROXY_URL;
        }

        @Override
        public String getConnectionUserName() {
            return "proxy_user";
        }
    }

    public static final class OracleCompatibleConnection extends org.h2.jdbc.JdbcConnection {
        private final org.h2.jdbc.JdbcConnection originalConnection;
        private int proxyType;
        private String proxyUserName;
        private boolean proxySession;
        private boolean remarksReporting;

        public OracleCompatibleConnection(org.h2.jdbc.JdbcConnection connection) throws SQLException {
            super(connection);
            this.originalConnection = connection;
        }

        public void openProxySession(int type, Properties properties) {
            this.proxyType = type;
            this.proxyUserName = properties.getProperty("PROXY_USER_NAME");
            this.proxySession = true;
        }

        public boolean isProxySession() {
            return proxySession;
        }

        public void setRemarksReporting(boolean remarksReporting) {
            this.remarksReporting = remarksReporting;
        }

        public int getProxyType() {
            return proxyType;
        }

        public String getProxyUserName() {
            return proxyUserName;
        }

        public boolean isRemarksReporting() {
            return remarksReporting;
        }

        @Override
        public synchronized void close() throws SQLException {
            originalConnection.close();
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            if ("{call DBMS_UTILITY.DB_VERSION(?,?)}".equals(sql)) {
                throw new SQLException("DBMS_UTILITY.DB_VERSION is not available in this test connection");
            }
            return super.prepareCall(sql);
        }
    }
}
