/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.ConnectionServiceFactory;
import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionServiceFactoryTest {

    @BeforeEach
    void resetBeforeTest() {
        ConnectionServiceFactory.reset();
    }

    @AfterEach
    void resetAfterTest() {
        ConnectionServiceFactory.reset();
    }

    @Test
    void createsHighestPriorityConnectionImplementationForJdbcUrl() {
        DatabaseConnection connection = ConnectionServiceFactory.getInstance()
                .getDatabaseConnection("jdbc:h2:mem:connectionServiceFactory");

        assertThat(connection).isInstanceOf(JdbcConnection.class);
        assertThat(connection.getPriority()).isPositive();
    }
}
