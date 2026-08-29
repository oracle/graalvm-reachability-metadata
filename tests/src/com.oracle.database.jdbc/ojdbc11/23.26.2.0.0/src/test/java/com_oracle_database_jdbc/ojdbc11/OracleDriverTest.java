/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import oracle.jdbc.driver.OracleDriver;
import org.junit.jupiter.api.Test;

public class OracleDriverTest {
    @Test
    void describesConfiguredConnectionProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(OracleDriver.user_string, "scott");
        OracleDriver driver = new OracleDriver();

        DriverPropertyInfo[] propertyInfo =
                driver.getPropertyInfo("jdbc:oracle:thin:@//database.example:1521/service", properties);

        DriverPropertyInfo userProperty = Arrays.stream(propertyInfo)
                .filter(info -> OracleDriver.user_string.equals(info.name))
                .findFirst()
                .orElseThrow();
        assertThat(userProperty.value).isEqualTo("scott");
        assertThat(propertyInfo).isNotEmpty();
    }

    @Test
    void rejectsAnIncompleteOracleUrl() {
        OracleDriver driver = new OracleDriver();

        assertThatThrownBy(() -> driver.connect("jdbc:oracle:thin:", new Properties()))
                .isInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }
}
