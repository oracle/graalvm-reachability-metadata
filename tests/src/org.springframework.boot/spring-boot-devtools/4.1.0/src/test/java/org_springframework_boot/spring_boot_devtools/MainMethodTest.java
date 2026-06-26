/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.FailureHandler;
import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;

public class MainMethodTest {

    @Test
    void restarterFindsMainMethodWithStringArrayArgument() throws Exception {
        final TestRestarter restarter = new TestRestarter(
                new ThreadWithMainStackTrace(MainMethodTestStringMainApplication.class));

        restarter.startAndAssertMainClassFound();
    }

    @Test
    void restarterFindsMainMethodWithNoArguments() throws Exception {
        final TestRestarter restarter = new TestRestarter(
                new ThreadWithMainStackTrace(MainMethodTestNoArgsMainApplication.class));

        restarter.startAndAssertMainClassFound();
    }

    private static final class ThreadWithMainStackTrace extends Thread {

        private final StackTraceElement[] stackTrace;

        private ThreadWithMainStackTrace(Class<?> mainClass) {
            this.stackTrace = new StackTraceElement[] {
                    new StackTraceElement("org.springframework.boot.loader.launch.Launcher", "main", null, 1),
                    new StackTraceElement(mainClass.getName(), "main", null, 1) };
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return this.stackTrace.clone();
        }

    }

    private static final class TestRestarter extends Restarter {

        private ClassLoader relaunchedClassLoader;

        private TestRestarter(Thread thread) {
            super(thread, new String[0], false, RestartInitializer.NONE);
        }

        @Override
        protected Throwable relaunch(ClassLoader classLoader) {
            this.relaunchedClassLoader = classLoader;
            return null;
        }

        private void startAndAssertMainClassFound() throws Exception {
            start(FailureHandler.NONE);

            assertNotNull(this.relaunchedClassLoader);
        }

    }

}

class MainMethodTestStringMainApplication {

    public static void main(String[] args) {
    }

}

class MainMethodTestNoArgsMainApplication {

    public static void main() {
    }

}
