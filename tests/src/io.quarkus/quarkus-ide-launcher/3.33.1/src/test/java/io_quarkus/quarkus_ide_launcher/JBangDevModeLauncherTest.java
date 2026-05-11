/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_ide_launcher;

import io.quarkus.bootstrap.jbang.JBangDevModeLauncherImpl;
import io.quarkus.launcher.JBangDevModeLauncher;
import io.quarkus.launcher.RuntimeLaunchClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JBangDevModeLauncherTest {
    private static final String SERIALIZED_APP_MODEL = "quarkus-internal.serialized-app-model.path";

    @Test
    void mainLoadsAndInvokesJbangDevModeLauncherImplementation() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalQuarkusDev = System.getProperty("quarkus.dev");
        String originalSerializedAppModel = System.getProperty(SERIALIZED_APP_MODEL);
        JBangDevModeLauncherImpl.reset();

        try {
            System.setProperty("quarkus.dev", "true");
            System.setProperty(SERIALIZED_APP_MODEL, "serialized-model");

            JBangDevModeLauncher.main("--debug", "example-app.java");

            assertThat(JBangDevModeLauncherImpl.invocationCount()).isEqualTo(1);
            assertThat(JBangDevModeLauncherImpl.args()).containsExactly("--debug", "example-app.java");
            assertThat(JBangDevModeLauncherImpl.quarkusDevProperty()).isNull();
            assertThat(JBangDevModeLauncherImpl.contextClassLoader()).isInstanceOf(RuntimeLaunchClassLoader.class);
            assertThat(System.getProperty(SERIALIZED_APP_MODEL)).isNull();
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(originalClassLoader);
        } finally {
            restoreProperty("quarkus.dev", originalQuarkusDev);
            restoreProperty(SERIALIZED_APP_MODEL, originalSerializedAppModel);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JBangDevModeLauncherImpl.reset();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
