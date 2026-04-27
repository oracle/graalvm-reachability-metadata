/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tools.ant.launch.AntMain;
import org.apache.tools.ant.launch.Launcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LauncherDynamicAccessTest {

    @Test
    void mainLoadsAndInstantiatesConfiguredAntMain() {
        String originalAntHome = System.getProperty(Launcher.ANTHOME_PROPERTY);
        String originalAntLibDir = System.getProperty(Launcher.ANTLIBDIR_PROPERTY);
        String originalJavaClassPath = System.getProperty("java.class.path");
        boolean originalLaunchDiag = Launcher.launchDiag;
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        RecordingAntMain.reset();
        try {
            Launcher.main(new String[] {
                    "--nouserlib",
                    "-main", RecordingAntMain.class.getName(),
                    "-f", "sample-build.xml",
                    "demo-target"
            });

            assertThat(RecordingAntMain.CONSTRUCTED.get()).isTrue();
            assertThat(RecordingAntMain.START_ANT_CALLED.get()).isTrue();
            assertThat(RecordingAntMain.RECEIVED_ARGUMENTS.get())
                    .containsExactly("-f", "sample-build.xml", "demo-target");
        } finally {
            restoreSystemProperty(Launcher.ANTHOME_PROPERTY, originalAntHome);
            restoreSystemProperty(Launcher.ANTLIBDIR_PROPERTY, originalAntLibDir);
            restoreSystemProperty("java.class.path", originalJavaClassPath);
            Launcher.launchDiag = originalLaunchDiag;
            Thread.currentThread().setContextClassLoader(originalTccl);
            RecordingAntMain.reset();
        }
    }

    private static void restoreSystemProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }

    public static final class RecordingAntMain implements AntMain {
        private static final AtomicBoolean CONSTRUCTED = new AtomicBoolean();
        private static final AtomicBoolean START_ANT_CALLED = new AtomicBoolean();
        private static final AtomicReference<List<String>> RECEIVED_ARGUMENTS = new AtomicReference<>();

        public RecordingAntMain() {
            CONSTRUCTED.set(true);
        }

        @Override
        public void startAnt(final String[] args, final Properties additionalUserProperties,
                final ClassLoader coreLoader) {
            START_ANT_CALLED.set(true);
            RECEIVED_ARGUMENTS.set(List.of(args));
        }

        private static void reset() {
            CONSTRUCTED.set(false);
            START_ANT_CALLED.set(false);
            RECEIVED_ARGUMENTS.set(null);
        }
    }
}
