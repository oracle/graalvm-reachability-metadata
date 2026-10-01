/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FuturesGetCheckedTest {
    public static class CheckedFailure extends Exception {
        public CheckedFailure() {
        }

        public CheckedFailure(String message) {
            super(message);
        }

        public CheckedFailure(Throwable cause) {
            super(cause);
        }
    }

    @Test
    void getsACompletedFutureWithACheckedExceptionType() throws Exception {
        assertThat(Futures.getChecked(
                Futures.immediateFuture("curator"), CheckedFailure.class)).isEqualTo("curator");
    }
}
