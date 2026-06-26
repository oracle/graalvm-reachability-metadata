/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

public class JdbcUtilsTest {
    private static final String JDBC_URL =
            NonWrappingH2Driver.URL_PREFIX
                    + "mem:otel_jdbc_c3p0;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void unwrapsC3p0ConnectionFromStatement() throws Exception {
        ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(NonWrappingH2Driver.class.getName());
        dataSource.setForceUseNamedDriverClass(true);
        dataSource.setJdbcUrl(JDBC_URL);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(1);
        dataSource.setAcquireIncrement(1);
        dataSource.setCheckoutTimeout(10_000);
        dataSource.setLoginTimeout(10);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThat(connection.getClass().getName())
                    .isEqualTo("com.mchange.v2.c3p0.impl.NewProxyConnection");

            Connection unwrappedConnection = JdbcUtils.connectionFromStatement(statement);
            assertThat(unwrappedConnection).isNotNull();
            assertThat(unwrappedConnection.getClass().getName())
                    .isNotEqualTo("com.mchange.v2.c3p0.impl.NewProxyConnection");

            Connection cachedUnwrappedConnection = JdbcUtils.connectionFromStatement(statement);
            assertThat(cachedUnwrappedConnection).isSameAs(unwrappedConnection);
            try (Statement unwrappedStatement = cachedUnwrappedConnection.createStatement()) {
                assertThat(unwrappedStatement.execute("SELECT 1")).isTrue();
            }
        } finally {
            dataSource.close();
        }
    }
}
