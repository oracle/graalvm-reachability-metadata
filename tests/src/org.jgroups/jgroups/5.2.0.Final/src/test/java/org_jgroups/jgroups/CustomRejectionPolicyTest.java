/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.CustomRejectionPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomRejectionPolicyTest {
    @Test
    void instantiatesAndDelegatesToConfiguredRejectedExecutionHandler() {
        CustomRejectionPolicy policy = new CustomRejectionPolicy(
            "custom=java.util.concurrent.ThreadPoolExecutor$CallerRunsPolicy");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
        AtomicBoolean taskRan = new AtomicBoolean();

        try {
            policy.rejectedExecution(() -> taskRan.set(true), executor);
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(taskRan).isTrue();
    }
}
