/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.driver.T2CConnection;
import org.junit.jupiter.api.Test;

public class BufferCacheTest {
    @Test
    void allocatesBucketedAndOversizedCharacterBuffers() throws Exception {
        Properties configuration = new Properties();
        configuration.setProperty(
                OracleConnection.CONNECTION_PROPERTY_USE_THREADLOCAL_BUFFER_CACHE, "false");
        configuration.setProperty(
                OracleConnection.CONNECTION_PROPERTY_MAX_CACHED_BUFFER_SIZE, "12");
        DetachedT2CConnection connection = new DetachedT2CConnection(configuration);

        char[] bucketed = connection.getCharBufferSync(100);
        char[] oversized = connection.getCharBufferSync(4097);

        assertThat(bucketed).hasSize(4096);
        assertThat(oversized).hasSize(4097);
    }

    private static final class DetachedT2CConnection extends T2CConnection {
        private DetachedT2CConnection(Properties properties) throws SQLException {
            super("jdbc:oracle:oci:@", properties, null);
        }
    }
}
