/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.junit_interface;

import munit.internal.junitinterface.Configurable;
import munit.internal.junitinterface.CustomFingerprint;
import munit.internal.junitinterface.CustomRunners;
import munit.internal.junitinterface.JUnitComputer;
import munit.internal.junitinterface.Settings;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.RunnerBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class JUnitComputerTest {
    @Test
    void loadsCustomRunnerAndInstantiatesItForMatchingSuite() throws Throwable {
        RecordingRunner.reset();
        Settings settings = Settings.defaults();
        CustomRunners customRunners = CustomRunners.of(
                CustomFingerprint.of(BaseSuite.class.getName(), RecordingRunner.class.getName()));
        JUnitComputer computer = new JUnitComputer(
                JUnitComputerTest.class.getClassLoader(),
                customRunners,
                settings);

        Runner suite = computer.getSuite(
                new UnusedRunnerBuilder(),
                new Class<?>[] { ExampleSuite.class });

        assertThat(RecordingRunner.constructedFor).isEqualTo(ExampleSuite.class);
        assertThat(RecordingRunner.configuredWith).isSameAs(settings);

        suite.run(new RunNotifier());

        assertThat(RecordingRunner.runCount).isOne();
    }

    public static class BaseSuite {
    }

    public static final class ExampleSuite extends BaseSuite {
    }

    public static final class RecordingRunner extends Runner implements Configurable {
        private static Class<?> constructedFor;
        private static Settings configuredWith;
        private static int runCount;

        public RecordingRunner(Class<?> testClass) {
            constructedFor = testClass;
        }

        @Override
        public Description getDescription() {
            return Description.createSuiteDescription(constructedFor);
        }

        @Override
        public void run(RunNotifier notifier) {
            runCount++;
        }

        @Override
        public void configure(Settings settings) {
            configuredWith = settings;
        }

        private static void reset() {
            constructedFor = null;
            configuredWith = null;
            runCount = 0;
        }
    }

    private static final class UnusedRunnerBuilder extends RunnerBuilder {
        @Override
        public Runner runnerForClass(Class<?> testClass) {
            throw new AssertionError("JUnitComputer should use the registered custom runner");
        }
    }
}
