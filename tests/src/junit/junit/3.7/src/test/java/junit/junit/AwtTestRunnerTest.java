/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.awt.Frame;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.awtui.TestRunner;
import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class AwtTestRunnerTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void loadsWindowIconAndRerunsSelectedFailure() throws InterruptedException {
        RerunnableTestCase.resetState();
        ExposedTestRunner runner = new ExposedTestRunner();
        runner.setLoading(false);
        runner.start(new String[] {RerunnableTestCase.class.getName()});
        Frame frame = runner.frame();
        Thread initialRun = runner.getRunner();
        try {
            assertThat(frame.getIconImage()).isNotNull();
            assertThat(initialRun).isNotNull();
            assertThat(RerunnableTestCase.started.await(10, TimeUnit.SECONDS)).isTrue();

            RerunnableTestCase.continueRun.countDown();
            initialRun.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(initialRun.isAlive()).isFalse();
            assertThat(RerunnableTestCase.runCount).isEqualTo(1);

            runner.rerun();

            assertThat(RerunnableTestCase.constructionCount).isEqualTo(2);
            assertThat(RerunnableTestCase.runCount).isEqualTo(2);
            assertThat(runner.statusText()).endsWith(" was successful");
        } finally {
            RerunnableTestCase.continueRun.countDown();
            if (initialRun != null && initialRun.isAlive()) {
                initialRun.interrupt();
                initialRun.join(TimeUnit.SECONDS.toMillis(10));
            }
            frame.dispose();
        }
    }

    public static class RerunnableTestCase extends TestCase {
        private static CountDownLatch started;
        private static CountDownLatch continueRun;
        private static int constructionCount;
        private static int runCount;

        public RerunnableTestCase(String name) {
            super(name);
            constructionCount++;
        }

        public void testSucceedsOnRerun() throws InterruptedException {
            runCount++;
            if (runCount == 1) {
                started.countDown();
                if (!continueRun.await(10, TimeUnit.SECONDS)) {
                    fail("Timed out while preparing the selected failure");
                }
                fail("First run supplies the selected failure");
            }
        }

        private static void resetState() {
            started = new CountDownLatch(1);
            continueRun = new CountDownLatch(1);
            constructionCount = 0;
            runCount = 0;
        }
    }

    public static class ExposedTestRunner extends TestRunner {
        public Frame frame() {
            return fFrame;
        }

        public String statusText() {
            return fStatusLine.getText();
        }
    }
}
