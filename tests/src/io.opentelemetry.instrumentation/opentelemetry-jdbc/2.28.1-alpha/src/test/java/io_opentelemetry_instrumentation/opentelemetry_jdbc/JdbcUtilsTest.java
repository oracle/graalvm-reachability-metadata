/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.impl.NewProxyConnection;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils;
import java.sql.Connection;
import java.util.Properties;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

public class JdbcUtilsTest {
    private static final String JDBC_URL =
            NonWrappingH2Driver.URL_PREFIX
                    + "mem:otel_jdbc_c3p0;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void unwrapsC3p0ProxyConnection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "sa");
        properties.setProperty("password", "");

        try (Connection innerConnection = new NonWrappingH2Driver().connect(JDBC_URL, properties)) {
            Connection connection = new NewProxyConnection(innerConnection);
            assertThat(connection.getClass().getName())
                    .isEqualTo("com.mchange.v2.c3p0.impl.NewProxyConnection");

            Connection unwrappedConnection = JdbcUtils.unwrapConnection(connection);
            assertThat(unwrappedConnection).isNotNull();
            assertThat(unwrappedConnection.getClass().getName())
                    .isNotEqualTo("com.mchange.v2.c3p0.impl.NewProxyConnection");
            assertThat(unwrappedConnection).isSameAs(innerConnection);

            Connection cachedUnwrappedConnection = JdbcUtils.unwrapConnection(connection);
            assertThat(cachedUnwrappedConnection).isSameAs(unwrappedConnection);
            try (Statement unwrappedStatement = cachedUnwrappedConnection.createStatement()) {
                assertThat(unwrappedStatement.execute("SELECT 1")).isTrue();
            }
        }
    }
}
