/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.apache.pulsar.shade.com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;

public class FuturesGetCheckedTest {

    @Test
    void wrapsAFailedFutureInTheRequestedCheckedException() {
        final IOException failure = new IOException("storage unavailable");

        assertThatThrownBy(() -> Futures.getChecked(Futures.immediateFailedFuture(failure), ClientFailure.class))
                .isInstanceOf(ClientFailure.class)
                .hasCause(failure);
    }

    public static final class ClientFailure extends Exception {
        public ClientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
