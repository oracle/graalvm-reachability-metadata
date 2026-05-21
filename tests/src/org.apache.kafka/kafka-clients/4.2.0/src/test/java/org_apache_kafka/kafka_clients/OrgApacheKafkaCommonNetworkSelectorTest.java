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
    @Timeout(30)
    void testImmediatelyConnectedSelectorCleanup() throws Throwable {
        executeSelectorTest(SelectorTest::testImmediatelyConnectedCleaned);
    }

    @Test
    @Timeout(30)
    void testLowestPriorityChannelUsesClosingChannels() throws Throwable {
        executeSelectorTest(SelectorTest::testLowestPriorityChannel);
    }

    private static void executeSelectorTest(ExecutableSelectorTest executable) throws Throwable {
        SelectorTest selectorTest = new SelectorTest();
        selectorTest.setUp();
        Throwable failure = null;
        try {
            executable.execute(selectorTest);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                selectorTest.tearDown();
            } catch (Throwable tearDownFailure) {
                if (failure != null) {
                    failure.addSuppressed(tearDownFailure);
                } else {
                    throw tearDownFailure;
                }
            }
        }
    }

    @FunctionalInterface
    private interface ExecutableSelectorTest {
        void execute(SelectorTest selectorTest) throws Exception;
    }
}
