/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleDriver;
import oracle.net.ns.NetException;
import org.junit.jupiter.api.Test;

public class OracleDriverTest {
    @Test
    void describesUrlCredentialsAndDefaultConnectionOptions() throws Exception {
        OracleDriver driver = new OracleDriver();
        DriverPropertyInfo[] options = driver.getPropertyInfo(
                "jdbc:oracle:thin:reader/secret@localhost:1521:sample", new Properties());
        Map<String, String> byName = Arrays.stream(options)
                .filter(option -> option.value != null)
                .collect(Collectors.toMap(option -> option.name, option -> option.value, (first, second) -> first));

        assertThat(byName).containsEntry("user", "reader").containsEntry("password", "secret");
        assertThat(byName).containsEntry(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH,
                OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH_DEFAULT);
        assertThat(driver.acceptsURL("jdbc:oracle:thin:@localhost:1521:sample")).isTrue();
        assertThat(driver.connect("jdbc:other:sample", new Properties())).isNull();
    }

    @Test
    void rejectsAnUnsupportedNetworkTransport() {
        Properties options = new Properties();
        options.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        options.setProperty("oracle.jdbc.ReadTimeout", "10000");
        String url = """
                jdbc:oracle:thin:@(DESCRIPTION=
                  (ADDRESS=(PROTOCOL=unsupported)(HOST=localhost)(PORT=1521))
                  (CONNECT_DATA=(SERVICE_NAME=sample)))
                """.strip();
        SQLException error = assertThrows(SQLException.class, () -> new OracleDriver().connect(url, options));

        assertThat(error.getErrorCode()).isEqualTo(17002);
        assertThat((Throwable) error).hasRootCauseInstanceOf(NetException.class)
                .hasStackTraceContaining("protocol: unsupported");
    }
}
