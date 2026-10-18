/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.com.google.common.util.concurrent.ClosingFuture;
import org.apache.pulsar.shade.com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;

public class ClosingFutureTest {

    @Test
    void finishesAnImmediateFutureAfterClosingItsResourceScope() throws Exception {
        final ClosingFuture<String> closing = ClosingFuture.from(Futures.immediateFuture("stored"));

        assertThat(closing.finishToFuture().get(10, TimeUnit.SECONDS)).isEqualTo("stored");
    }
}
