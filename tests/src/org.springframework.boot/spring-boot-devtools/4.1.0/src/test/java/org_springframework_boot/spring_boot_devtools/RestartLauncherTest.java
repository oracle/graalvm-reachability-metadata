/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.FailureHandler;
import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class RestartLauncherTest {

    @Test
    void restarterRelaunchInvokesMainMethodWithRestartClassLoader() throws Exception {
        String[] args = {"one", "two"};
        RestartLauncherApplication.reset();
        TestRestarter restarter = new TestRestarter(args);

        restarter.start();

        assertThat(RestartLauncherApplication.invoked).isTrue();
        assertThat(RestartLauncherApplication.arguments).containsExactly("one", "two");
        assertThat(RestartLauncherApplication.threadName).isEqualTo("restartedMain");
        assertThat(RestartLauncherApplication.contextClassLoader).isInstanceOf(RestartClassLoader.class);
    }

    private static StackTraceElement stackTraceElementForMainMethod() {
        return new StackTraceElement(RestartLauncherApplication.class.getName(), "main",
                RestartLauncherApplication.class.getSimpleName() + ".java", 1);
    }

    private static final class TestRestarter extends Restarter {

        private TestRestarter(String[] args) {
            super(new MainMethodThread(), args, false, RestartInitializer.NONE);
        }

        private void start() throws Exception {
            start(FailureHandler.NONE);
        }

    }

    private static final class MainMethodThread extends Thread {

        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[]{stackTraceElementForMainMethod()};
        }

    }

}

class RestartLauncherApplication {

    static boolean invoked;

    static String[] arguments;

    static String threadName;

    static ClassLoader contextClassLoader;

    static void reset() {
        invoked = false;
        arguments = null;
        threadName = null;
        contextClassLoader = null;
    }

    static void main(String[] args) {
        invoked = true;
        arguments = args.clone();
        threadName = Thread.currentThread().getName();
        contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

}
