/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.LobCreationContext;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.internal.LobCreatorBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LobCreatorBuilderTest {

    @Test
    public void createsAContextualClobWithTheJdbcConnection() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:lob-creator")) {
            LobCreator creator = new LobCreatorBuilder(Map.of(), connection)
                    .buildLobCreator(new ConnectionBackedLobCreationContext(connection));
            Clob clob = creator.createClob("hibernate lob");
            try {
                assertThat(clob.getSubString(1, (int) clob.length())).isEqualTo("hibernate lob");
            } finally {
                clob.free();
            }
        }
    }

    private static final class ConnectionBackedLobCreationContext implements LobCreationContext {
        private final Connection connection;

        private ConnectionBackedLobCreationContext(Connection connection) {
            this.connection = connection;
        }

        @Override
        public <T> T execute(Callback<T> callback) {
            try {
                return callback.executeOnConnection(connection);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
