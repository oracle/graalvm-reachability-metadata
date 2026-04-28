/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.integration.IQv2IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class IQv2IntegrationTestDynamicAccessTest {

    @BeforeAll
    static void startCluster() throws Exception {
        IQv2IntegrationTest.before();
    }

    @AfterAll
    static void stopCluster() {
        IQv2IntegrationTest.after();
    }

    @Test
    void shouldRejectQueriesForNonRunningActiveTasks(TestInfo testInfo) throws Exception {
        IQv2IntegrationTest test = new IQv2IntegrationTest();
        test.beforeTest(testInfo);
        try {
            test.shouldRejectNonRunningActive();
        } finally {
            test.afterTest();
        }
    }
}
