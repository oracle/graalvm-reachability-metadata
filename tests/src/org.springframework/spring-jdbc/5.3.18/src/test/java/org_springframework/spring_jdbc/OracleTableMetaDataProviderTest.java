/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.metadata.OracleTableMetaDataProvider;
import org.springframework.jdbc.core.metadata.TableParameterMetaData;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleTableMetaDataProviderTest {
    @Test
    void usesStandardJdbcMetadataWhenVendorConnectionApiIsUnavailable() throws Exception {
        try (Connection connection = JdbcTestSupport.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE customers (id INTEGER NOT NULL, display_name VARCHAR(80))");
            DatabaseMetaData metadata = connection.getMetaData();
            OracleTableMetaDataProvider provider = new OracleTableMetaDataProvider(metadata, true);
            provider.initializeWithMetaData(metadata);
            provider.initializeWithTableColumnMetaData(metadata, null, "PUBLIC", "customers");

            assertThat(provider.isTableColumnMetaDataUsed()).isTrue();
            assertThat(provider.getTableParameterMetaData()).extracting(TableParameterMetaData::getParameterName)
                    .containsExactly("ID", "DISPLAY_NAME");
            TableParameterMetaData id = provider.getTableParameterMetaData().get(0);
            assertThat(id.getSqlType()).isEqualTo(Types.INTEGER);
            assertThat(id.isNullable()).isFalse();
            TableParameterMetaData name = provider.getTableParameterMetaData().get(1);
            assertThat(name.getSqlType()).isEqualTo(Types.VARCHAR);
            assertThat(name.isNullable()).isTrue();
        }
    }
}
