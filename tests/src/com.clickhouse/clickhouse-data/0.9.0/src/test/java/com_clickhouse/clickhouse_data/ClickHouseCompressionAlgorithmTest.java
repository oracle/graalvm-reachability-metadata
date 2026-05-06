/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseCompression;
import com.clickhouse.data.ClickHouseCompressionAlgorithm;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseCompressionAlgorithmTest {
    @Test
    void createsDetectedCompressionImplementation() {
        ClickHouseCompressionAlgorithm algorithm = ClickHouseCompressionAlgorithm.of(ClickHouseCompression.DEFLATE);

        assertThat(algorithm.getAlgorithm()).isEqualTo(ClickHouseCompression.DEFLATE);
    }

    @Test
    void createsDefaultCompressionImplementationWhenLibraryDetectionIsDisabled() {
        String propertyName = "chc_gzip_lib_detection";
        String previousValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "false");
        try {
            ClickHouseCompressionAlgorithm algorithm = ClickHouseCompressionAlgorithm.of(ClickHouseCompression.GZIP);

            assertThat(algorithm.getAlgorithm()).isEqualTo(ClickHouseCompression.GZIP);
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }
}
