/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.tools.ant.launch.AntMain;
import org.apache.tools.ant.launch.Launcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LauncherTest {
    private static final MethodType RUN_METHOD_TYPE = MethodType.methodType(int.class, String[].class);

    private String previousAntHome;
    private String previousAntLibraryDirectory;
    private String previousJavaClassPath;
    private ClassLoader previousContextClassLoader;
    private boolean previousLaunchDiagnostics;

    @BeforeEach
    void rememberLauncherState() {
        previousAntHome = System.getProperty(Launcher.ANTHOME_PROPERTY);
        previousAntLibraryDirectory = System.getProperty(Launcher.ANTLIBDIR_PROPERTY);
        previousJavaClassPath = System.getProperty("java.class.path");
        previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        previousLaunchDiagnostics = Launcher.launchDiag;
    }

    @AfterEach
    void restoreLauncherState() {
        restoreProperty(Launcher.ANTHOME_PROPERTY, previousAntHome);
        restoreProperty(Launcher.ANTLIBDIR_PROPERTY, previousAntLibraryDirectory);
        restoreProperty("java.class.path", previousJavaClassPath);
        Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        Launcher.launchDiag = previousLaunchDiagnostics;
    }

    @Test
    void reportsIncompatibleMainClassAfterLoadingIt() throws Throwable {
        int exitCode = runLauncher(
            "--nouserlib",
            "--noclasspath",
            "-main",
            AntMain.class.getName()
        );

        assertThat(exitCode).isEqualTo(2);
    }

    private static int runLauncher(String... arguments) throws Throwable {
        return (int) launcherRun().invoke(new Launcher(), arguments);
    }

    private static MethodHandle launcherRun() throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.privateLookupIn(Launcher.class, MethodHandles.lookup())
            .findVirtual(Launcher.class, "run", RUN_METHOD_TYPE);
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
