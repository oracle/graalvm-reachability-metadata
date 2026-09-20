/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.clients.consumer.internals.FetcherTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OrgApacheKafkaClientsConsumerInternalsFetcherTestAnonymous4Anonymous1Test {

    @Test
    @Timeout(60)
    void traversesFetchSessionPartitionsDuringConcurrentFetches() throws Exception {
        FetcherTest fetcherTest = new FetcherTest();
        fetcherTest.setup();
        try {
            fetcherTest.testFetcherConcurrency();
        } finally {
            fetcherTest.teardown();
        }
    }
}
