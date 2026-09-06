/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JdbcIndicatorTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final int LOGIN_TIMEOUT_SECONDS = 10;
    private static final String JDBC_URL = "jdbc:custom://database.example:6543/orders";

    @Test
    @Timeout(55)
    void reportsAnUnavailableJdbcDataSourceThroughTheHealthEndpoint() {
        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "endpoints.health.details-visible", "ANONYMOUS",
                "endpoints.health.disk-space.enabled", false,
                "endpoints.health.deadlocked-threads.enabled", false,
                "endpoints.health.discovery-client-health.enabled", false,
                "endpoints.health.service-ready-indicator-enabled", false);

        UnavailableDataSource dataSource = new UnavailableDataSource(JDBC_URL);
        try (ApplicationContext context = ApplicationContext.builder()
                .environments(Environment.TEST)
                .properties(properties)
                .singletons(dataSource)
                .start()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            try (server; HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
                HttpRequest<?> request = HttpRequest.GET("/health").accept(MediaType.APPLICATION_JSON_TYPE);
                HttpResponse<String> response =
                        client.toBlocking().exchange(request, Argument.STRING, Argument.STRING);

                assertThat(response.code()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
                assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_TYPE);
                assertThat(response.body())
                        .contains("jdbc", "DOWN", "database.example:6543/orders", "Database is unavailable");
            }
        }
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        configuration.setExceptionOnErrorStatus(false);
        return configuration;
    }

    public static final class UnavailableDataSource implements DataSource {
        private final String url;
        private PrintWriter logWriter;
        private int loginTimeout = LOGIN_TIMEOUT_SECONDS;

        public UnavailableDataSource(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Database is unavailable");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Database is unavailable");
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(UnavailableDataSource.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Data source does not wrap " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
