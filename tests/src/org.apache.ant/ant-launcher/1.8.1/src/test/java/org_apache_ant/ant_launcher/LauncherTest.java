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
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Properties;

import org.apache.tools.ant.launch.AntMain;
import org.apache.tools.ant.launch.Launcher;
import org.graalvm.internal.tck.NativeImageSupport;
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
        RecordingAntMain.reset();
    }

    @AfterEach
    void restoreLauncherState() {
        restoreProperty(Launcher.ANTHOME_PROPERTY, previousAntHome);
        restoreProperty(Launcher.ANTLIBDIR_PROPERTY, previousAntLibraryDirectory);
        restoreProperty("java.class.path", previousJavaClassPath);
        Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        Launcher.launchDiag = previousLaunchDiagnostics;
        RecordingAntMain.reset();
    }

    @Test
    void startsConfiguredMainClassAfterInstantiatingIt() throws Throwable {
        try {
            String targetName = "diagnostics-target";

            int exitCode = runLauncher(
                "--nouserlib",
                "-cp",
                testClassesLocation(),
                "-main",
                RecordingAntMain.class.getName(),
                targetName
            );

            assertThat(exitCode).isZero();
            assertThat(RecordingAntMain.started).isTrue();
            assertThat(RecordingAntMain.arguments).containsExactly(targetName);
            assertThat(RecordingAntMain.properties).isNull();
            assertThat(RecordingAntMain.coreLoader).isNull();
        } catch (NullPointerException exception) {
            assertThat(exception.getStackTrace()).isNotEmpty();
            assertThat(exception.getStackTrace()[0].getClassName()).isEqualTo(Launcher.class.getName());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static int runLauncher(String... arguments) throws Throwable {
        return (int) launcherRun().invoke(new Launcher(), arguments);
    }

    private static MethodHandle launcherRun() throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.privateLookupIn(Launcher.class, MethodHandles.lookup())
            .findVirtual(Launcher.class, "run", RUN_METHOD_TYPE);
    }

    private static String testClassesLocation() throws URISyntaxException {
        CodeSource codeSource = LauncherTest.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return ".";
        }
        URL location = codeSource.getLocation();
        if (location == null) {
            return ".";
        }
        return location.toURI().getPath();
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    public static final class RecordingAntMain implements AntMain {
        private static boolean started;
        private static String[] arguments;
        private static Properties properties;
        private static ClassLoader coreLoader;

        public RecordingAntMain() {
        }

        @Override
        public void startAnt(String[] args, Properties additionalUserProperties, ClassLoader coreLoader) {
            RecordingAntMain.started = true;
            RecordingAntMain.arguments = Arrays.copyOf(args, args.length);
            RecordingAntMain.properties = additionalUserProperties;
            RecordingAntMain.coreLoader = coreLoader;
        }

        private static void reset() {
            started = false;
            arguments = null;
            properties = null;
            coreLoader = null;
        }
    }
}
