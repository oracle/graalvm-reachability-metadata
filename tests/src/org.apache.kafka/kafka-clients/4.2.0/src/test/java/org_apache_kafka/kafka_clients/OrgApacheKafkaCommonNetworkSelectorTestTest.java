/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.network.SelectorTest;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaCommonNetworkSelectorTestTest {

    @Test
    void testImmediatelyConnectedChannelsAreCleaned() throws Exception {
        runSelectorTest(SelectorTest::testImmediatelyConnectedCleaned);
    }

    @Test
    void testLowestPriorityChannelSelection() throws Exception {
        runSelectorTest(SelectorTest::testLowestPriorityChannel);
    }

    private static void runSelectorTest(SelectorTestAction action) throws Exception {
        SelectorTest selectorTest = new SelectorTest();
        selectorTest.setUp();
        try {
            action.run(selectorTest);
        } finally {
            selectorTest.tearDown();
        }
    }

    @FunctionalInterface
    private interface SelectorTestAction {
        void run(SelectorTest selectorTest) throws Exception;
    }
}
