/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.JDBCParameterMetaData;
import org.junit.jupiter.api.Test;

public class JDBCParameterMetaDataTest {
    @Test
    public void preparedStatementParameterMetadataToStringInspectsMetadataMethods() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setDatabase(inMemoryDatabase("parameter_metadata"));
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE CUSTOMER (
                        ID INTEGER PRIMARY KEY,
                        NAME VARCHAR(40) NOT NULL,
                        CREDIT_LIMIT DECIMAL(10, 2)
                    )
                    """);

            try (PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT ID
                    FROM CUSTOMER
                    WHERE ID = ? AND NAME = ? AND CREDIT_LIMIT = ?
                    """)) {
                ParameterMetaData metadata = preparedStatement.getParameterMetaData();

                assertThat(metadata).isInstanceOf(JDBCParameterMetaData.class);
                assertThat(metadata.getParameterCount()).isEqualTo(3);

                String description = metadata.toString();

                assertThat(description)
                        .isNotBlank()
                        .contains(JDBCParameterMetaData.class.getName())
                        .doesNotContain("UnsupportedFeature");
                assertThat(metadata.isWrapperFor(ParameterMetaData.class)).isTrue();
            }
        }
    }

    private static String inMemoryDatabase(String prefix) {
        return "mem:" + prefix + "_" + Long.toUnsignedString(System.nanoTime()) + ";shutdown=true";
    }
}
