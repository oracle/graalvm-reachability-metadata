/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import org.junit.jupiter.api.Test;

public class CompactObjectInputStreamTest {
    @Test
    void resolvesTheSerializedClassDescriptor() {
        NettyObjectSerializationSupport.roundTrip();
    }

    @Test
    void fallsBackToDefaultClassResolutionForFatDescriptor() {
        NettyObjectSerializationSupport.roundTripArrayWithDefaultResolverFallback();
    }
}
