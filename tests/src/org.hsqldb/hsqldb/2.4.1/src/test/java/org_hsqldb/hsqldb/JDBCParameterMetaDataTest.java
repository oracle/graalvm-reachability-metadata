/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDBCParameterMetaDataTest {
    @Test
    void describesTypedPreparedStatementParameters() throws Exception {
        try (Connection connection = DatabaseSupport.memory();
                PreparedStatement statement = connection.prepareStatement(
                        "VALUES (CAST(? AS INTEGER), CAST(? AS VARCHAR(40)))")) {
            ParameterMetaData metadata = statement.getParameterMetaData();
            assertEquals(2, metadata.getParameterCount());
            assertEquals(Types.INTEGER, metadata.getParameterType(1));
            assertEquals(Types.VARCHAR, metadata.getParameterType(2));
            assertEquals(40, metadata.getPrecision(2));
            assertEquals("java.lang.Integer", metadata.getParameterClassName(1));
            assertEquals(ParameterMetaData.parameterModeIn, metadata.getParameterMode(1));
        }
    }
}
