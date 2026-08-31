/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.driver.T2CConnection;
import org.junit.jupiter.api.Test;

public class PhysicalConnectionTest {
    @Test
    void exposesConfiguredPropertiesAndMappedJavaTypes() throws Exception {
        Properties configuration = new Properties();
        configuration.setProperty(
                OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "37");
        configuration.setProperty(
                OracleConnection.CONNECTION_PROPERTY_DEFAULT_CONNECTION_VALIDATION, "COMPLETE");
        configuration.setProperty(
                OracleConnection.CONNECTION_PROPERTY_WALLET_LOCATION, "test-wallet");
        DetachedT2CConnection connection = new DetachedT2CConnection(configuration);

        assertThat(connection.getProperties())
                .containsEntry(
                        OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "37")
                .containsEntry(
                        OracleConnection.CONNECTION_PROPERTY_DEFAULT_CONNECTION_VALIDATION,
                        "COMPLETE")
                .containsEntry(
                        OracleConnection.CONNECTION_PROPERTY_WALLET_LOCATION, "test-wallet");

        String sqlType = "APP.TEST_TYPE";
        connection.registerSQLType(sqlType, MappedValue.class.getName());

        assertThat(connection.getSQLType(new MappedValue())).isEqualTo(sqlType);
        assertThat(connection.getJavaObject(sqlType)).isInstanceOf(MappedValue.class);
        assertThat(connection.classForNameAndSchema(MappedValue.class.getName(), "APP"))
                .isEqualTo(MappedValue.class);
    }

    public static final class MappedValue {
        public MappedValue() { }
    }

    private static final class DetachedT2CConnection extends T2CConnection {
        private DetachedT2CConnection(Properties properties) throws SQLException {
            super("jdbc:oracle:oci:@", properties, null);
        }
    }
}
