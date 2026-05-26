/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:15438/" + DATABASE;
    private static Process process;

    private static Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, properties);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for Parser tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "15438:5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-parser-stdout.txt"))
                .redirectError(new File("opengauss-parser-stderr.txt")).start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for Parser tests started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for Parser tests");
            process.destroy();
        }
    }

    @Test
    void executesJdbcEscapedFunction() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT {fn concat('open','gauss')}")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("opengauss");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
