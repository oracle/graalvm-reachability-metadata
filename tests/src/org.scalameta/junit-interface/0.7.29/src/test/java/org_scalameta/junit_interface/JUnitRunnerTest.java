/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.junit_interface;

import munit.internal.junitinterface.JUnitFramework;
import org.junit.jupiter.api.Test;
import org.junit.runner.notification.RunListener;
import sbt.testing.AnnotatedFingerprint;
import sbt.testing.Fingerprint;
import sbt.testing.Runner;
import sbt.testing.Selector;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;

public class JUnitRunnerTest {
    @Test
    void createsConfiguredRunListenerAndLoadsTaskClass() {
        RecordingRunListener.reset();
        JUnitFramework framework = new JUnitFramework();
        String listenerArgument = "--run-listener=" + RecordingRunListener.class.getName();
        Runner runner = framework.runner(
                new String[] {listenerArgument },
                new String[] {"remote-argument" },
                JUnitRunnerTest.class.getClassLoader());
        Fingerprint fingerprint = junitFingerprint(framework.fingerprints());
        TaskDef taskDef = new TaskDef(
                JUnit4Suite.class.getName(),
                fingerprint,
                false,
                new Selector[0]);

        Task[] tasks = runner.tasks(new TaskDef[] {taskDef });

        assertThat(RecordingRunListener.constructedCount).isOne();
        assertThat(tasks).hasSize(1);
        assertThat(tasks[0].taskDef().fullyQualifiedName()).isEqualTo(JUnit4Suite.class.getName());
        assertThat(runner.remoteArgs()).containsExactly("remote-argument");
        assertThat(runner.args()).containsExactly(listenerArgument);
    }

    private static Fingerprint junitFingerprint(Fingerprint[] fingerprints) {
        for (Fingerprint fingerprint : fingerprints) {
            if (fingerprint instanceof AnnotatedFingerprint) {
                AnnotatedFingerprint annotated = (AnnotatedFingerprint) fingerprint;
                if ("org.junit.Test".equals(annotated.annotationName())) {
                    return fingerprint;
                }
            }
        }
        throw new AssertionError("JUnit test fingerprint was not exposed by the framework");
    }

    public static final class RecordingRunListener extends RunListener {
        private static int constructedCount;

        public RecordingRunListener() {
            constructedCount++;
        }

        private static void reset() {
            constructedCount = 0;
        }
    }

    public static final class JUnit4Suite {
        @org.junit.Test
        public void succeeds() {
            assertThat("junit").startsWith("jun");
        }
    }
}
