/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.integration.QueryableStateIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

public class QueryableStateIntegrationTestDynamicAccessTest {

    @Test
    @Timeout(600)
    void shouldInitializeQueryableStateInputValues(TestInfo testInfo) throws Exception {
        QueryableStateIntegrationTest.startCluster();
        QueryableStateIntegrationTest test = new QueryableStateIntegrationTest();
        boolean initialized = false;
        try {
            try {
                test.before(testInfo);
                initialized = true;
            } finally {
                if (initialized) {
                    test.shutdown();
                }
            }
        } finally {
            QueryableStateIntegrationTest.closeCluster();
        }
    }
}
