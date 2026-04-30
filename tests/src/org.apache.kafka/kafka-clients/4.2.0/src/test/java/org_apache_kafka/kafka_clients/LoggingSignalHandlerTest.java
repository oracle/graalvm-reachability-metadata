/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.kafka.common.utils.LoggingSignalHandler;
import org.junit.jupiter.api.Test;

public class LoggingSignalHandlerTest {
    @Test
    void registersTerminationSignalHandlers() {
        assertDoesNotThrow(() -> new LoggingSignalHandler().register());
    }
}
