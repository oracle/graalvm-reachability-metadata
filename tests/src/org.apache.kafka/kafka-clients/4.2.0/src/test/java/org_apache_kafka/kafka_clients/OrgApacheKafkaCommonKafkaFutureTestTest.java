/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.KafkaFutureTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OrgApacheKafkaCommonKafkaFutureTestTest {

    @Test
    @Timeout(55)
    void testCompletableFutureViewRejectsDirectMutation() throws Throwable {
        KafkaFutureTest kafkaFutureTest = new KafkaFutureTest();

        kafkaFutureTest.testLeakCompletableFuture();
    }
}
