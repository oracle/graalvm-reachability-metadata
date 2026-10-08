/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.database.ExtendedConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class ExtendedConnectionDataSourceProxyTest {
    @Test
    void createsConnectionProxyThatSuppressesCloseUntilStopped() throws Exception {
        DriverManagerDataSource targetDataSource =
                new DriverManagerDataSource("jdbc:h2:mem:close_suppression", "sa", "");
        ExtendedConnectionDataSourceProxy dataSource =
                new ExtendedConnectionDataSourceProxy(targetDataSource);
        dataSource.afterPropertiesSet();

        Connection connection = dataSource.getConnection();
        Connection targetConnection = ((ConnectionProxy) connection).getTargetConnection();
        try {
            dataSource.startCloseSuppression(connection);
            connection.close();
            assertThat(targetConnection.isClosed()).isFalse();

            dataSource.stopCloseSuppression(connection);
            connection.close();
            assertThat(targetConnection.isClosed()).isTrue();
        } finally {
            if (!targetConnection.isClosed()) {
                targetConnection.close();
            }
        }
    }
}
