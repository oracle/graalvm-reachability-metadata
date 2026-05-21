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

public class OrgApacheKafkaCommonNetworkSelectorTestTest {

    @Test
    @Timeout(30)
    void immediatelyConnectedChannelsAreCleanedByKafkaSelectorTestHarness() throws Exception {
        runSelectorTest(SelectorTest::testImmediatelyConnectedCleaned);
    }

    @Test
    @Timeout(30)
    void lowestPriorityChannelAccountsForClosingChannels() throws Exception {
        runSelectorTest(SelectorTest::testLowestPriorityChannel);
    }

    private static void runSelectorTest(SelectorTestInvocation invocation) throws Exception {
        SelectorTest selectorTest = new SelectorTest();
        selectorTest.setUp();
        try {
            invocation.run(selectorTest);
        } finally {
            selectorTest.tearDown();
        }
    }

    @FunctionalInterface
    private interface SelectorTestInvocation {
        void run(SelectorTest selectorTest) throws Exception;
    }
}
