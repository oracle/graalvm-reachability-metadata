/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.debug.AfterCloseLoggingConnectionWrapper;
import com.mchange.v2.sql.filter.FilterConnection;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

public class AfterCloseLoggingConnectionWrapperTest {
    @Test
    void wrapsConnectionWithAfterCloseLoggingProxy() throws SQLException {
        TrackingConnection innerConnection = new TrackingConnection();

        Connection wrappedConnection = AfterCloseLoggingConnectionWrapper.wrap(innerConnection);

        assertThat(wrappedConnection).isInstanceOf(Connection.class);
        assertThat(wrappedConnection).isNotSameAs(innerConnection);

        wrappedConnection.close();

        assertThat(innerConnection.isClosed()).isTrue();
        assertThat(wrappedConnection.isClosed()).isTrue();
    }

    private static final class TrackingConnection extends FilterConnection {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public String toString() {
            return "TrackingConnection";
        }
    }
}
