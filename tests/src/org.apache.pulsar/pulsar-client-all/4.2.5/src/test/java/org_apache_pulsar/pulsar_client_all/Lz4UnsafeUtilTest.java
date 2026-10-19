/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import org.apache.pulsar.shade.io.airlift.compress.lz4.Lz4Compressor;
import org.apache.pulsar.shade.io.airlift.compress.lz4.Lz4Decompressor;
import org.junit.jupiter.api.Test;

public class Lz4UnsafeUtilTest {
    @Test
    void roundTripsRepeatedData() {
        CompressionSupport.roundTrip(new Lz4Compressor(), new Lz4Decompressor());
    }
}
