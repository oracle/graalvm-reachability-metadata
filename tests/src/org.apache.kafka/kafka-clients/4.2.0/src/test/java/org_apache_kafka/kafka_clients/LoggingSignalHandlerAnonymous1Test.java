/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.LoggingSignalHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingSignalHandlerAnonymous1Test {

    @Test
    void installsProxyBackedJvmSignalHandlers() throws Exception {
        LoggingSignalHandler handler = new LoggingSignalHandler();

        handler.register();

        assertThat(handler).isNotNull();
    }
}
