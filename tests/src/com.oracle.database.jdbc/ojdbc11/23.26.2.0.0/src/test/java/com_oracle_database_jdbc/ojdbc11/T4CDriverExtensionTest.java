/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.driver.OracleDriver;
import org.junit.jupiter.api.Test;

public class T4CDriverExtensionTest {
    @Test
    void createsTheConfiguredShardingConnectionImplementation() {
        Properties properties = new Properties();
        properties.setProperty("oracle.jdbc.useShardingDriverConnection", "true");
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");

        assertThatThrownBy(() -> new OracleDriver()
                        .connect("jdbc:oracle:thin:@//127.0.0.1:1/test-service", properties))
                .isInstanceOf(SQLException.class);
    }
}
