/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.Driver;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class CalciteMetaImplTest {
    @Test
    public void createsJdbcMetadataResultSetsForCatalogs() throws SQLException {
        Connection connection = new Driver().connect("jdbc:calcite:", new Properties());
        assertThat(connection).isNotNull();

        try (Connection closeableConnection = connection;
             ResultSet catalogs = closeableConnection.getMetaData().getCatalogs()) {
            ResultSetMetaData metaData = catalogs.getMetaData();

            assertThat(metaData.getColumnCount()).isEqualTo(1);
            assertThat(metaData.getColumnName(1)).isEqualTo("TABLE_CAT");
            assertThat(catalogs.next()).isTrue();
            assertThat(catalogs.next()).isFalse();
        }
    }
}
