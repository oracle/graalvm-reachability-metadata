/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCParameterMetaData;
import org.junit.jupiter.api.Test;

public class JDBCParameterMetaDataTest {
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    @Test
    public void reachesReflectiveStringRenderingForPreparedStatementParameters() throws Exception {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE PARAMETER_METADATA_TEST ("
                        + "ID INTEGER, NAME VARCHAR(40), AMOUNT DECIMAL(12, 2))");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO PARAMETER_METADATA_TEST (ID, NAME, AMOUNT) VALUES (?, ?, ?)")) {
                ParameterMetaData parameterMetaData = statement.getParameterMetaData();

                assertThat(parameterMetaData).isInstanceOf(JDBCParameterMetaData.class);
                assertThat(parameterMetaData.getParameterCount()).isEqualTo(3);
                assertThat(parameterMetaData.getParameterClassName(1)).isEqualTo(Integer.class.getName());
                assertThat(parameterMetaData.getParameterType(1)).isEqualTo(Types.INTEGER);
                assertThat(parameterMetaData.getParameterTypeName(1)).isEqualTo("INTEGER");
                assertThat(parameterMetaData.getParameterClassName(2)).isEqualTo(String.class.getName());
                assertThat(parameterMetaData.getParameterType(2)).isEqualTo(Types.VARCHAR);
                assertThat(parameterMetaData.getParameterTypeName(2)).isEqualTo("VARCHAR");
                assertThat(parameterMetaData.getParameterClassName(3)).isEqualTo(BigDecimal.class.getName());
                assertThat(parameterMetaData.getParameterType(3)).isEqualTo(Types.DECIMAL);
                assertThat(parameterMetaData.getParameterTypeName(3)).isEqualTo("DECIMAL");
                assertThat(parameterMetaData.getPrecision(3)).isEqualTo(12);
                assertThat(parameterMetaData.getScale(3)).isEqualTo(2);

                String rendered = parameterMetaData.toString();

                assertThat(rendered)
                        .contains(JDBCParameterMetaData.class.getName())
                        .contains("toStringImpl_exception");
            }
        }
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:hsqldb:mem:parametermetadata" + DATABASE_COUNTER.incrementAndGet(),
                "SA", "");
    }
}
