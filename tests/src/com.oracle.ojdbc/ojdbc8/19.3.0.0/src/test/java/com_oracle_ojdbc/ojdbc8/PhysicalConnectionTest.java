/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.Test;

public class PhysicalConnectionTest {
    @Test
    void exposesConnectionOptionsAndInstantiatesRegisteredSqlMappings() throws Exception {
        Properties options = new Properties();
        options.setProperty(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "31");
        options.setProperty(OracleConnection.CONNECTION_PROPERTY_USER_NAME, "reader");
        options.setProperty(OracleConnection.CONNECTION_PROPERTY_PASSWORD, "not-exported");
        OfflineConnection connection = new OfflineConnection(options);

        Properties exported = connection.getProperties();
        assertThat(exported).containsEntry(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "31")
                .containsEntry(OracleConnection.CONNECTION_PROPERTY_USER_NAME, "READER")
                .doesNotContainKey(OracleConnection.CONNECTION_PROPERTY_PASSWORD);

        connection.registerSQLType("APP.CONTACT", ApplicationValue.class.getName());
        Object mapped = connection.getJavaObject("APP.CONTACT");
        assertThat(mapped).isInstanceOf(ApplicationValue.class);
        assertThat(connection.getSQLType(mapped)).isEqualTo("APP.CONTACT");
        assertThat(connection.classForNameAndSchema(ApplicationValue.class.getName(), "APP"))
                .isEqualTo(ApplicationValue.class);
    }
}
