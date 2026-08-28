/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import oracle.jdbc.OracleConnectionWrapper;
import org.junit.jupiter.api.Test;

public class OracleConnectionWrapperInnerCloseInvocationHandlerTest {
    @Test
    void closesTheWrapperThroughAnExtensionProxy() throws Exception {
        CloseTrackingConnection connection = new CloseTrackingConnection();
        OracleConnectionWrapper wrapper = new OracleConnectionWrapper(connection);

        wrapper.unwrap(CloseProbe.class).close();

        assertThat(connection.isCloseCalled()).isTrue();
    }

    public interface CloseProbe extends AutoCloseable {}

    private static final class CloseTrackingConnection extends OracleConnectionWrapper {
        private boolean closeCalled;

        @Override
        public void close() {
            closeCalled = true;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface == CloseProbe.class) {
                return iface.cast(new CloseProbeTarget());
            }
            return super.unwrap(iface);
        }

        private boolean isCloseCalled() {
            return closeCalled;
        }
    }

    private static final class CloseProbeTarget implements CloseProbe {
        @Override
        public void close() {
            throw new AssertionError("The wrapper must handle close calls");
        }
    }
}
