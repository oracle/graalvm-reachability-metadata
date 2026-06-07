/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.jdbc_v2;

import com.clickhouse.jdbc.internal.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    @Test
    void convertListCreatesNestedTypedArrays() throws SQLException {
        List<?> values = List.of(
                List.of(1, 2),
                List.of(3, 4));

        Object converted = JdbcUtils.convertList(values, String.class, 2);

        assertThat(converted).isInstanceOf(String[][].class);
        String[][] actual = (String[][]) converted;
        assertThat(actual[0]).containsExactly("1", "2");
        assertThat(actual[1]).containsExactly("3", "4");
    }

    @Test
    void convertArrayCreatesNestedTypedArrays() throws SQLException {
        Object values = new Object[] {
                new Object[] {1, 2},
                new Object[] {3, 4}
        };

        Object converted = JdbcUtils.convertArray(values, String.class, 2);

        assertThat(converted).isInstanceOf(String[][].class);
        String[][] actual = (String[][]) converted;
        assertThat(actual[0]).containsExactly("1", "2");
        assertThat(actual[1]).containsExactly("3", "4");
    }
}
