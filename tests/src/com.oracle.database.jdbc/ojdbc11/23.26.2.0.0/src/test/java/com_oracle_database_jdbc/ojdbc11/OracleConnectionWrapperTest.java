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

public class OracleConnectionWrapperTest {
    @Test
    void wrapsAnUnwrappedExtensionInterface() throws Exception {
        OracleConnectionWrapper wrapper = new OracleConnectionWrapper(new ExtensionConnection());

        ExtensionProbe probe = wrapper.unwrap(ExtensionProbe.class);

        assertThat(probe.message()).isEqualTo("from-connection");
        assertThat(wrapper.unwrap(ExtensionProbe.class)).isSameAs(probe);
    }

    public interface Extension {
        String message();
    }

    public interface ExtensionProbe extends Extension {}

    private static final class ExtensionConnection extends OracleConnectionWrapper implements Extension {
        @Override
        public String message() {
            return "from-connection";
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface == ExtensionProbe.class) {
                return iface.cast(new ExtensionProbeTarget());
            }
            return super.unwrap(iface);
        }
    }

    private static final class ExtensionProbeTarget implements ExtensionProbe {
        @Override
        public String message() {
            return "from-unwrapped-object";
        }
    }
}
