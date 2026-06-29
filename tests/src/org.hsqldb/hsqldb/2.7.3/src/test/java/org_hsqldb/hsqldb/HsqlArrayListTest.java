/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.lib.HsqlArrayList;
import org.junit.jupiter.api.Test;

public class HsqlArrayListTest {
    @Test
    void arrayAggregateGrowsAndMaterializesInternalListThroughJdbc() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE array_list_source(id INTEGER, label VARCHAR(20))");

            for (int i = 0; i < 10; i++) {
                statement.executeUpdate("""
                        INSERT INTO array_list_source(id, label)
                        VALUES (%d, 'label_%02d')
                        """.formatted(i, i));
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT ARRAY_AGG(label ORDER BY id)
                    FROM array_list_source
                    """)) {
                assertThat(resultSet.next()).isTrue();

                Array sqlArray = resultSet.getArray(1);

                try {
                    Object[] labels = (Object[]) sqlArray.getArray();

                    assertThat(labels).containsExactly(
                            "label_00", "label_01", "label_02", "label_03", "label_04",
                            "label_05", "label_06", "label_07", "label_08", "label_09");
                    assertThat(resultSet.next()).isFalse();
                } finally {
                    sqlArray.free();
                }
            }
        }
    }

    @Test
    void typedListGrowthKeepsComponentTypeWhenResized() {
        HsqlArrayList<?> first = new HsqlArrayList<>();
        HsqlArrayList<?> second = new HsqlArrayList<>();
        HsqlArrayList<HsqlArrayList<?>> list = new HsqlArrayList<>(
                new HsqlArrayList<?>[] {first }, 1, false);

        list.add(second);

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getArray()).isInstanceOf(HsqlArrayList[].class);
        assertThat(list.getArray()).containsExactly(first, second);
    }

    @Test
    void toArrayCreatesArrayWithListComponentType() {
        HsqlArrayList<?> first = new HsqlArrayList<>();
        HsqlArrayList<?> second = new HsqlArrayList<>();
        HsqlArrayList<HsqlArrayList<?>> list = new HsqlArrayList<>(
                new HsqlArrayList<?>[] {first, second }, 2, false);

        Object[] array = list.toArray();

        assertThat(array).isInstanceOf(HsqlArrayList[].class);
        assertThat(array).containsExactly(first, second);
        assertThat(array).isNotSameAs(list.getArray());
    }

    @Test
    void typedToArrayCreatesLargerTargetArrayWhenArgumentIsTooSmall() {
        HsqlArrayList<?> first = new HsqlArrayList<>();
        HsqlArrayList<?> second = new HsqlArrayList<>();
        HsqlArrayList<HsqlArrayList<?>> list = new HsqlArrayList<>(
                new HsqlArrayList<?>[] {first, second }, 2, false);
        HsqlArrayList<?>[] target = new HsqlArrayList<?>[1];

        HsqlArrayList<?>[] array = list.toArray(target);

        assertThat(array).isInstanceOf(HsqlArrayList[].class);
        assertThat(array).containsExactly(first, second);
        assertThat(array).isNotSameAs(target);
    }

    private static Connection openConnection() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + randomDatabaseName() + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String randomDatabaseName() {
        return "HsqlArrayListTest" + UUID.randomUUID().toString().replace("-", "");
    }
}
