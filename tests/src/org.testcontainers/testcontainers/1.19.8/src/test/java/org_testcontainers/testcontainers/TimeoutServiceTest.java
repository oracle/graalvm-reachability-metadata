/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.util.TimeoutService;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeoutServiceTest {
    @Test
    void executesHandlersAtTheirRequestedDeadline() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);

        TimeoutService.addTimeoutHandler(System.currentTimeMillis(), invoked::countDown);

        assertThat(invoked.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
