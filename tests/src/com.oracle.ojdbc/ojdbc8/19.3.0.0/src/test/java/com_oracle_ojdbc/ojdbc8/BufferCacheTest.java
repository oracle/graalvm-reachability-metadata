/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.Test;

public class BufferCacheTest {
    @Test
    void allocatesCharacterBuffersInsideAndBeyondTheCacheLimit() throws Exception {
        Properties options = new Properties();
        options.setProperty(OracleConnection.CONNECTION_PROPERTY_USE_THREADLOCAL_BUFFER_CACHE, "false");
        options.setProperty(OracleConnection.CONNECTION_PROPERTY_MAX_CACHED_BUFFER_SIZE, "12");
        OfflineConnection connection = new OfflineConnection(options);

        char[] small = connection.getCharBufferSync(123);
        char[] large = connection.getCharBufferSync(5000);

        assertThat(small).hasSize(4096).containsOnly('\0');
        assertThat(large).hasSize(5000).containsOnly('\0');
        small[0] = 'A';
        assertThat(connection.getCharBufferSync(123)).isNotSameAs(small).containsOnly('\0');
    }
}
