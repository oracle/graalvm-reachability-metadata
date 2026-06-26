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

public class MainMethodTest {

    @Test
    void restarterFindsMainMethodWithStringArrayArguments() throws Exception {
        TestRestarter restarter = new TestRestarter(MainMethodStringArgumentsApplication.class);

        restarter.startWithoutRelaunching();

        assertThat(restarter.relaunchCalled).isTrue();
    }

    @Test
    void restarterFallsBackToNoArgumentMainMethod() throws Exception {
        TestRestarter restarter = new TestRestarter(MainMethodNoArgumentsApplication.class);

        restarter.startWithoutRelaunching();

        assertThat(restarter.relaunchCalled).isTrue();
    }

    private static StackTraceElement stackTraceElementForMainMethod(Class<?> applicationClass) {
        return new StackTraceElement(applicationClass.getName(), "main", applicationClass.getSimpleName() + ".java", 1);
    }

    private static final class TestRestarter extends Restarter {

        private boolean relaunchCalled;

        private TestRestarter(Class<?> applicationClass) {
            super(new MainMethodThread(stackTraceElementForMainMethod(applicationClass)), new String[0], false,
                    RestartInitializer.NONE);
        }

        private void startWithoutRelaunching() throws Exception {
            start(FailureHandler.NONE);
        }

        @Override
        protected Throwable relaunch(ClassLoader classLoader) {
            assertThat(classLoader).isInstanceOf(RestartClassLoader.class);
            this.relaunchCalled = true;
            return null;
        }

    }

    private static final class MainMethodThread extends Thread {

        private final StackTraceElement stackTraceElement;

        private MainMethodThread(StackTraceElement stackTraceElement) {
            this.stackTraceElement = stackTraceElement;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[] { this.stackTraceElement };
        }

    }

}

class MainMethodStringArgumentsApplication {

    static void main(String[] args) {
    }

}

class MainMethodNoArgumentsApplication {

    static void main() {
    }

}
