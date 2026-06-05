/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.network.SaslChannelBuilderTest;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaCommonNetworkSaslChannelBuilderTestTest {

    @Test
    void testClientChannelBuilderWithBrokerConfigs() throws Exception {
        SaslChannelBuilderTest saslChannelBuilderTest = new SaslChannelBuilderTest();
        try {
            saslChannelBuilderTest.testClientChannelBuilderWithBrokerConfigs();
        } finally {
            saslChannelBuilderTest.tearDown();
        }
    }
}
