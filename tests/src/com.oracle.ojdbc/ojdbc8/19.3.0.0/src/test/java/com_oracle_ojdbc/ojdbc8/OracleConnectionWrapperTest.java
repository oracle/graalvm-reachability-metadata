/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.OracleConnectionWrapper;
import org.junit.jupiter.api.Test;

public class OracleConnectionWrapperTest {
    @Test
    void cachesTheUnwrappedApplicationExtension() throws Exception {
        OfflineConnection connection = new OfflineConnection();
        OracleConnectionWrapper wrapper = new OracleConnectionWrapper(connection);

        ConnectionNote extension = wrapper.unwrap(ConnectionNote.class);

        assertThat(extension.note()).isEqualTo("application connection");
        assertThat(wrapper.unwrap(ConnectionNote.class)).isSameAs(extension);
        assertThat(extension).isNotSameAs(connection);
    }
}
