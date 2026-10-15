/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import org.apache.pulsar.shade.com.google.common.util.concurrent.Futures;
import org.apache.pulsar.shade.com.google.common.util.concurrent.ListenableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class FuturesGetCheckedTest {
    @Test
    void convertsFailedFutureToRequestedCheckedException() {
        IOException failure = new IOException("lookup unavailable");
        ListenableFuture<String> future = Futures.immediateFailedFuture(failure);

        Throwable thrown = catchThrowable(() -> Futures.getChecked(
                future, BrokerLookupException.class));

        assertThat(thrown)
                .isInstanceOf(BrokerLookupException.class)
                .hasMessageContaining("lookup unavailable")
                .hasCause(failure);
    }

    public static final class BrokerLookupException extends Exception {
        public BrokerLookupException(String message) {
            super(message);
        }

        public BrokerLookupException(Throwable cause) {
            super(cause);
        }
    }
}
