/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;

public class JDBCParameterMetaDataTest {
    @Test
    void formatsParameterMetadataFromPreparedStatement() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:mem:parameter_metadata_reflection");
                PreparedStatement statement = connection.prepareStatement(
                        "VALUES (CAST(? AS INTEGER))")) {
            ParameterMetaData parameterMetaData = statement.getParameterMetaData();

            assertThat(parameterMetaData.getParameterCount()).isEqualTo(1);
            assertThat(parameterMetaData.toString()).contains("JDBCParameterMetaData");
        }
    }
}
