/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_threads.jboss_threads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.threads.JBossExecutors;
import org.junit.jupiter.api.Test;

public class ThreadLocalResettingRunnableInnerResetterTest {
    @Test
    void resettingThreadFactoryClearsThreadLocalFields() throws InterruptedException {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        AtomicReference<Boolean> threadLocalWasReset = new AtomicReference<>(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ThreadFactory observingThreadFactory = task -> new Thread(() -> {
            try {
                task.run();
                threadLocalWasReset.set(threadLocal.get() == null);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "jboss-threads-thread-local-resetter-test");
        ThreadFactory resettingThreadFactory = JBossExecutors.resettingThreadFactory(observingThreadFactory);

        Thread thread = resettingThreadFactory.newThread(() -> threadLocal.set("value before reset"));

        thread.start();
        thread.join(10_000L);

        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(threadLocalWasReset.get()).isTrue();
    }
}
