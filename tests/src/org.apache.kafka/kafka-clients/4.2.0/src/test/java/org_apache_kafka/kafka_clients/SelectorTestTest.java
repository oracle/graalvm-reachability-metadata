/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.network.SelectorTest;
import org.junit.jupiter.api.Test;

public class SelectorTestTest {

    @Test
    void verifiesSelectorCleanupForImmediatelyConnectedChannels() throws Exception {
        SelectorTest test = new SelectorTest();
        test.setUp();
        try {
            test.testImmediatelyConnectedCleaned();
        } finally {
            test.tearDown();
        }
    }

    @Test
    void verifiesLowestPriorityChannelSelection() throws Exception {
        SelectorTest test = new SelectorTest();
        test.setUp();
        try {
            test.testLowestPriorityChannel();
        } finally {
            test.tearDown();
        }
    }
}
