/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.clients.consumer.internals.FetcherTest;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

public class OrgApacheKafkaClientsConsumerInternalsFetcherTestAnonymous4Anonymous1Test {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testConcurrentFetchSessionVerification() throws Exception {
        FetcherTest fetcherTest = new FetcherTest();
        fetcherTest.setup();
        try {
            fetcherTest.testFetcherConcurrency();
        } catch (Exception exception) {
            if (!hasUnsupportedFeatureError(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!hasUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            fetcherTest.teardown();
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current != current.getCause()) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
