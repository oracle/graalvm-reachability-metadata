/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.tools.ant.launch.AntMain;
import org.apache.tools.ant.launch.Launcher;
import org.junit.jupiter.api.Test;

public class LauncherTest implements AntMain {
    private static final String[] BUILD_ARGS = {"compile", "-Dexample=true"};
    private static String[] receivedArgs;
    private static Properties receivedProperties;
    private static ClassLoader receivedCoreLoader;

    @Test
    public void loadsAndInstantiatesConfiguredMainClass() {
        String originalAntHome = System.getProperty(Launcher.ANTHOME_PROPERTY);
        String originalAntLibraryDir = System.getProperty(Launcher.ANTLIBDIR_PROPERTY);
        String originalJavaClassPath = System.getProperty("java.class.path");
        boolean originalLaunchDiag = Launcher.launchDiag;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        receivedArgs = null;
        receivedProperties = null;
        receivedCoreLoader = null;

        try {
            if (originalJavaClassPath == null || originalJavaClassPath.isEmpty()) {
                System.setProperty("java.class.path", ".");
            }

            Launcher.main(new String[] {
                "--nouserlib",
                "--noclasspath",
                "-main",
                LauncherTest.class.getName(),
                BUILD_ARGS[0],
                BUILD_ARGS[1]
            });

            assertThat(receivedArgs).containsExactly(BUILD_ARGS);
            assertThat(receivedProperties).isNull();
            assertThat(receivedCoreLoader).isNull();
        } finally {
            restoreProperty(Launcher.ANTHOME_PROPERTY, originalAntHome);
            restoreProperty(Launcher.ANTLIBDIR_PROPERTY, originalAntLibraryDir);
            restoreProperty("java.class.path", originalJavaClassPath);
            Launcher.launchDiag = originalLaunchDiag;
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Override
    public void startAnt(String[] args, Properties additionalUserProperties, ClassLoader coreLoader) {
        receivedArgs = args;
        receivedProperties = additionalUserProperties;
        receivedCoreLoader = coreLoader;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
