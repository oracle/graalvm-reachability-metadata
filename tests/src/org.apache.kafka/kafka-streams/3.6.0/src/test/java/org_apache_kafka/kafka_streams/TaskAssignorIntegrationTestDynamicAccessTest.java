/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.integration.TaskAssignorIntegrationTest;
import org.junit.jupiter.api.Test;

public class TaskAssignorIntegrationTestDynamicAccessTest {

    @Test
    void shouldConfigureCustomTaskAssignorThroughKafkaStreamsStartup() throws Exception {
        TaskAssignorIntegrationTest.startCluster();
        try {
            TaskAssignorIntegrationTest test = new TaskAssignorIntegrationTest();
            test.shouldProperlyConfigureTheAssignor();
        } finally {
            TaskAssignorIntegrationTest.closeCluster();
        }
    }
}
