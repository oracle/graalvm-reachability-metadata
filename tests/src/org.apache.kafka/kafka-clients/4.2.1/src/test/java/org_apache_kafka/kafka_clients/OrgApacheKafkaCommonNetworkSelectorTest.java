/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.network.SelectorTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OrgApacheKafkaCommonNetworkSelectorTest {

    @Test
    @Timeout(60)
    void testLowestPriorityChannel() throws Exception {
        SelectorTest selectorTest = new SelectorTest();
        selectorTest.setUp();
        try {
            selectorTest.testLowestPriorityChannel();
        } finally {
            selectorTest.tearDown();
        }
    }

    @Test
    @Timeout(60)
    void testImmediatelyConnectedKeysAreCleaned() throws Exception {
        SelectorTest selectorTest = new SelectorTest();
        selectorTest.setUp();
        try {
            selectorTest.testImmediatelyConnectedCleaned();
        } finally {
            selectorTest.tearDown();
        }
    }
}
