/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleConnectionWrapper;
import org.junit.jupiter.api.Test;

public class OracleConnectionWrapperTest {
    @Test
    void wrapsAnUnwrappedExtensionInterface() throws Exception {
        ExtensionConnection connection = new ExtensionConnection();
        OracleConnectionWrapper wrapper = new OracleConnectionWrapper(connection);

        ExtensionProbe probe = wrapper.unwrap(ExtensionProbe.class);

        assertThat(connection.getWrapper()).isSameAs(wrapper);
        assertThat(probe.message()).isEqualTo("from-connection");
        assertThat(wrapper.unwrap(ExtensionProbe.class)).isSameAs(probe);
    }

    public interface Extension {
        String message();
    }

    public interface ExtensionProbe extends Extension {}

    private static final class ExtensionConnection extends OracleConnectionWrapper implements Extension {
        private OracleConnection wrapper;

        @Override
        public String message() {
            return "from-connection";
        }

        @Override
        public void setWrapper(OracleConnection wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface == ExtensionProbe.class) {
                return iface.cast(new ExtensionProbeTarget());
            }
            return super.unwrap(iface);
        }

        private OracleConnection getWrapper() {
            return wrapper;
        }
    }

    private static final class ExtensionProbeTarget implements ExtensionProbe {
        @Override
        public String message() {
            return "from-unwrapped-object";
        }
    }
}
