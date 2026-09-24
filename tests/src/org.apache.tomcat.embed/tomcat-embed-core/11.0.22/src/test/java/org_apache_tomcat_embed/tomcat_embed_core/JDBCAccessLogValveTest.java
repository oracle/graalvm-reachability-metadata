/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.valves.JDBCAccessLogValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class JDBCAccessLogValveTest {

    private static final String DATABASE_URL = "jdbc:h2:mem:tomcat-access-log;DB_CLOSE_DELAY=-1";

    @Test
    void recordsCompletedRequestsUsingConfiguredJdbcDriver(@TempDir Path baseDirectory) throws Exception {
        createAccessLogTable();
        JDBCAccessLogValve valve = new JDBCAccessLogValve();
        valve.setDriverName("org.h2.Driver");
        valve.setConnectionURL(DATABASE_URL);
        valve.setTableName("request_log");
        valve.setRemoteHostField("remote_host");
        valve.setUserField("user_name");
        valve.setTimestampField("request_time");
        valve.setQueryField("request_uri");
        valve.setStatusField("response_status");
        valve.setBytesField("response_bytes");

        try (EmbeddedTomcatSupport server = new EmbeddedTomcatSupport(baseDirectory, new GreetingServlet())) {
            server.replaceErrorReportValve(valve);
            server.start();

            HttpResponse<String> response = server.request("GET");

            assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.body()).isEqualTo("hello");
        }

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT request_uri, response_status, response_bytes FROM request_log")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("request_uri")).isEqualTo("/test");
            assertThat(rows.getInt("response_status")).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(rows.getInt("response_bytes")).isEqualTo(5);
            assertThat(rows.next()).isFalse();
        }
    }

    private static void createAccessLogTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE request_log (
                        remote_host VARCHAR(255),
                        user_name VARCHAR(255),
                        request_time TIMESTAMP,
                        request_uri VARCHAR(1024),
                        response_status INTEGER,
                        response_bytes INTEGER
                    )
                    """);
        }
    }

    private static final class GreetingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().print("hello");
        }
    }
}
