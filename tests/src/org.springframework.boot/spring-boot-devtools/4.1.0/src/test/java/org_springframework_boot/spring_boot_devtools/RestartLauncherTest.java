/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.FailureHandler;
import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

public class RestartLauncherTest {

    @BeforeEach
    void resetApplication() {
        RestartLauncherTestApplication.reset();
    }

    @Test
    void relaunchInvokesMainMethodThroughRestartLauncher() throws Exception {
        final String[] args = {"first", "second"};
        final TestRestarter restarter = new TestRestarter(RestartLauncherTestApplication.class, args);

        restarter.startApplication();

        assertArrayEquals(args, RestartLauncherTestApplication.getArguments());
        assertInstanceOf(RestartClassLoader.class, RestartLauncherTestApplication.getContextClassLoader());
    }

    private static final class TestRestarter extends Restarter {

        private TestRestarter(Class<?> mainClass, String[] args) {
            super(new ThreadWithMainStackTrace(mainClass), args, false, RestartInitializer.NONE);
        }

        private void startApplication() throws Exception {
            start(FailureHandler.NONE);
        }

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

}

class RestartLauncherTestApplication {

    private static String[] arguments;

    private static ClassLoader contextClassLoader;

    public static void main(String[] args) {
        arguments = Arrays.copyOf(args, args.length);
        contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    static void reset() {
        arguments = null;
        contextClassLoader = null;
    }

    static String[] getArguments() {
        return arguments;
    }

    static ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

}
