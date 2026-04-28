/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryAwareThreadPoolExecutorTest {
    @Test
    void enablesCoreThreadTimeoutDuringConstruction() {
        MemoryAwareThreadPoolExecutor executor = new MemoryAwareThreadPoolExecutor(
                1,
                0,
                0,
                1,
                TimeUnit.SECONDS);

        try {
            assertThat(executor.allowsCoreThreadTimeOut()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
