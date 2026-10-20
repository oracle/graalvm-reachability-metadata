/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.kylin.jdbc.Driver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KylinMetaTest {
    @Test
    void exposesJdbcTableTypesFromTheKylinMetadataImplementation() throws Exception {
        AuthenticationHandler authenticationHandler = new AuthenticationHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kylin/api/user/authentication", authenticationHandler);
        server.start();

        Properties properties = new Properties();
        properties.setProperty("user", "test-user");
        properties.setProperty("password", "test-password");
        String expectedCredentials = Base64.getEncoder()
                .encodeToString("test-user:test-password".getBytes(StandardCharsets.UTF_8));

        try (Connection connection = new Driver().connect(
                        "jdbc:kylin://127.0.0.1:" + server.getAddress().getPort() + "/sales", properties);
                ResultSet tableTypes = connection.getMetaData().getTableTypes()) {
            List<String> names = new ArrayList<>();
            while (tableTypes.next()) {
                names.add(tableTypes.getString("TABLE_TYPE"));
            }

            assertThat(names).contains("TABLE");
            assertThat(authenticationHandler.requestMethod).isEqualTo("POST");
            assertThat(authenticationHandler.authorization).isEqualTo("Basic " + expectedCredentials);
        } finally {
            server.stop(0);
        }
    }

    public static class AuthenticationHandler implements HttpHandler {
        private volatile String requestMethod;
        private volatile String authorization;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestMethod = exchange.getRequestMethod();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().close();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
