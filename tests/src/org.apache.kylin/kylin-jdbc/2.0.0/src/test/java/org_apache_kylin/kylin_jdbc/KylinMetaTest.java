/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kylin.jdbc.Driver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KylinMetaTest {
    @Test
    void exposesJdbcTableTypesFromTheKylinMetadataImplementation() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "test-user");
        properties.setProperty("password", "test-password");

        try (Connection connection = new Driver().connect("jdbc:kylin://localhost/sales", properties);
                ResultSet tableTypes = connection.getMetaData().getTableTypes()) {
            List<String> names = new ArrayList<>();
            while (tableTypes.next()) {
                names.add(tableTypes.getString("TABLE_TYPE"));
            }

            assertThat(names).contains("TABLE");
        }
    }
}
