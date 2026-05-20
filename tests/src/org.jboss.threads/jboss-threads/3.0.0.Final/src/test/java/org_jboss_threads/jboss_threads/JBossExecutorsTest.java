/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_threads.jboss_threads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.jboss.threads.JBossExecutors;
import org.junit.jupiter.api.Test;

public class JBossExecutorsTest {
    @Test
    void preservesCapturedContextClassLoaderWhileTaskRuns() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader alternateClassLoader = new TestClassLoader(originalClassLoader);
        AtomicReference<ClassLoader> observedClassLoader = new AtomicReference<>();

        Runnable task = JBossExecutors.classLoaderPreservingTask(
                () -> observedClassLoader.set(thread.getContextClassLoader()));

        try {
            thread.setContextClassLoader(alternateClassLoader);
            task.run();

            assertThat(observedClassLoader.get()).isSameAs(originalClassLoader);
            assertThat(thread.getContextClassLoader()).isSameAs(alternateClassLoader);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        private TestClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
