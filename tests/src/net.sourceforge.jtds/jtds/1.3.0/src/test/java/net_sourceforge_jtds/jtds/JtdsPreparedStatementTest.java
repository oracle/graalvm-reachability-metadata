/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JtdsPreparedStatementTest {
    @Test
    void describesBoundParametersAndExecutesThePreparedQuery(@TempDir Path directory) throws Exception {
        try (SqlServerDatabase database = SqlServerDatabase.start(directory);
             Connection connection = database.openConnection("");
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT CAST(? AS int) AS id,
                            CAST(? AS nvarchar(80)) AS label,
                            CAST(? AS decimal(10, 2)) AS amount
                     """)) {
            statement.setQueryTimeout(15);
            statement.setInt(1, 27);
            statement.setString(2, "caf\u00e9");
            statement.setBigDecimal(3, new BigDecimal("19.75"));

            ParameterMetaData metadata = statement.getParameterMetaData();
            assertThat(metadata.getParameterCount()).isEqualTo(3);
            assertThat(metadata.getParameterType(1)).isEqualTo(Types.INTEGER);
            assertThat(metadata.getParameterClassName(1)).isEqualTo("java.lang.Integer");
            assertThat(metadata.isSigned(1)).isTrue();
            assertThat(metadata.getParameterType(2)).isEqualTo(Types.VARCHAR);
            assertThat(metadata.getParameterClassName(2)).isEqualTo("java.lang.String");
            assertThat(metadata.getParameterType(3)).isEqualTo(Types.DECIMAL);
            assertThat(metadata.getScale(3)).isEqualTo(2);
            for (int index = 1; index <= metadata.getParameterCount(); index++) {
                assertThat(metadata.getParameterMode(index)).isEqualTo(ParameterMetaData.parameterModeIn);
            }

            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("id")).isEqualTo(27);
                assertThat(result.getString("label")).isEqualTo("caf\u00e9");
                assertThat(result.getBigDecimal("amount")).isEqualByComparingTo("19.75");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
