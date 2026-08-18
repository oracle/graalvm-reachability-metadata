/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;

import static org.assertj.core.api.Assertions.assertThat;

public class RestartLauncherTest {

    @Test
    void restartsApplicationByInvokingItsMainMethod() throws InterruptedException {
        RestartedApplication.reset();
        Restarter restarter = new TestRestarter(RestartedApplication.class);

        restarter.restart();

        assertThat(RestartedApplication.awaitLaunch()).isTrue();
    }

    public static class RestartedApplication {

        private static CountDownLatch launchLatch = new CountDownLatch(1);

        public static void main(String[] args) {
            launchLatch.countDown();
        }

        private static void reset() {
            launchLatch = new CountDownLatch(1);
        }

        private static boolean awaitLaunch() throws InterruptedException {
            return launchLatch.await(10, TimeUnit.SECONDS);
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
