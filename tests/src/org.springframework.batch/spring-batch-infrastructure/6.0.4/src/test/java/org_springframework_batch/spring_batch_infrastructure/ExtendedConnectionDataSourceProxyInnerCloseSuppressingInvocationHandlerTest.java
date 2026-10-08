/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.database.ExtendedConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class ExtendedConnectionDataSourceProxyInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void delegatesJdbcOperationsToTargetConnection() throws Exception {
        DriverManagerDataSource targetDataSource =
                new DriverManagerDataSource("jdbc:h2:mem:delegated_operations", "sa", "");
        ExtendedConnectionDataSourceProxy dataSource =
                new ExtendedConnectionDataSourceProxy(targetDataSource);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table messages (id integer primary key, text varchar(32))");
            statement.executeUpdate("insert into messages values (1, 'processed')");

            try (ResultSet resultSet =
                    statement.executeQuery("select text from messages where id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("processed");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}
