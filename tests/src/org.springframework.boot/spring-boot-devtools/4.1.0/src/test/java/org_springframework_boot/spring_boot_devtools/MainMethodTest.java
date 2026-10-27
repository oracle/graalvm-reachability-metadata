/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;

import static org.assertj.core.api.Assertions.assertThat;

public class MainMethodTest {

    @Test
    void detectsStringArrayAndNoArgumentMainMethodsFromApplicationThreads() {
        Restarter stringArrayRestarter = new TestRestarter(StringArrayMain.class);
        Restarter noArgumentRestarter = new TestRestarter(NoArgumentMain.class);

        assertThat(stringArrayRestarter.getInitialUrls()).isNull();
        assertThat(noArgumentRestarter.getInitialUrls()).isNull();
    }

    public static class StringArrayMain {

        public static void main(String[] args) {
        }
    }

    public static class NoArgumentMain {

        public static void main() {
        }
    }

    private static final class TestRestarter extends Restarter {

        private TestRestarter(Class<?> applicationClass) {
            super(new ApplicationThread(applicationClass), new String[0], false, RestartInitializer.NONE);
        }
    }

    private static final class ApplicationThread extends Thread {

        private final StackTraceElement[] stackTrace;

        private ApplicationThread(Class<?> applicationClass) {
            this.stackTrace = new StackTraceElement[] {
                    new StackTraceElement(applicationClass.getName(), "main", null, -1) };
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return this.stackTrace.clone();
        }
    }
}
