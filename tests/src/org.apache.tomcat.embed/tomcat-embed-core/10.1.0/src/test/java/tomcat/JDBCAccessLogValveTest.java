/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import org.apache.catalina.valves.JDBCAccessLogValve;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBCAccessLogValveTest {
    @TempDir
    Path directory;

    @Test
    void recordsHttpAccessUsingConfiguredJdbcDriver() throws Exception {
        String url = "jdbc:h2:mem:http-access";
        try (Connection connection = DriverManager.getConnection(url, "reader", "secret");
                Statement statement = connection.createStatement();
                EmbeddedServer server = new EmbeddedServer(directory)) {
            statement.execute("""
                    CREATE TABLE request_log (
                        remote_host VARCHAR(255), user_name VARCHAR(255), request_time TIMESTAMP,
                        request_uri VARCHAR(255), response_status INT, response_bytes BIGINT)
                    """);
            JDBCAccessLogValve valve = new JDBCAccessLogValve();
            valve.setDriverName(Driver.class.getName());
            valve.setConnectionURL(url);
            valve.setConnectionName("reader");
            valve.setConnectionPassword("secret");
            valve.setTableName("request_log");
            valve.setRemoteHostField("remote_host");
            valve.setUserField("user_name");
            valve.setTimestampField("request_time");
            valve.setQueryField("request_uri");
            valve.setStatusField("response_status");
            valve.setBytesField("response_bytes");
            server.context().getPipeline().addValve(valve);
            server.servlet(new HttpServletTest.ReadServlet());
            server.start();
            assertThat(server.request("GET", "/catalog").statusCode()).isEqualTo(200);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            boolean recorded = false;
            while (!recorded && System.nanoTime() < deadline) {
                try (ResultSet rows = statement.executeQuery("SELECT request_uri, response_status FROM request_log")) {
                    if (rows.next()) {
                        assertThat(rows.getString(1)).isEqualTo("/catalog");
                        assertThat(rows.getInt(2)).isEqualTo(200);
                        recorded = true;
                    }
                }
                if (!recorded) {
                    Thread.sleep(10);
                }
            }
            assertThat(recorded).isTrue();
        }
    }
}
