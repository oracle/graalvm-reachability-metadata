/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_proguard.proguard_gradle;

import java.io.File;
import java.util.Map;

import org.gradle.api.tasks.TaskInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import proguard.gradle.ProGuardTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Proguard_gradleTest {

    @Test
    @Timeout(60)
    void proGuardTaskRejectsDirectInstantiationOutsideGradleDsl() {
        assertThatThrownBy(ProGuardTask::new)
                .isInstanceOf(TaskInstantiationException.class)
                .satisfies(exception -> assertThat(exception)
                        .hasMessageContaining(ProGuardTask.class.getName())
                        .hasMessageContaining("instantiated directly")
                        .hasMessageContaining("DSL"));
    }

    @Test
    @Timeout(60)
    void proGuardTaskPublishesJarAndConfigurationDslMethods() {
        final JarConfiguration inputJars = ProGuardTask::injars;
        final FilteredJarConfiguration filteredInputJars = ProGuardTask::injars;
        final JarConfiguration outputJars = ProGuardTask::outjars;
        final FilteredJarConfiguration filteredOutputJars = ProGuardTask::outjars;
        final JarConfiguration libraryJars = ProGuardTask::libraryjars;
        final FilteredJarConfiguration filteredLibraryJars = ProGuardTask::libraryjars;
        final JarConfiguration configuration = ProGuardTask::configuration;
        final File jarFile = new File("build/libs/application.jar");
        final Map<?, ?> filters = Map.of("filter", "!META-INF/MANIFEST.MF");

        assertThatThrownBy(() -> inputJars.configure(null, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> filteredInputJars.configure(null, filters, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> outputJars.configure(null, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> filteredOutputJars.configure(null, filters, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> libraryJars.configure(null, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> filteredLibraryJars.configure(null, filters, jarFile))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configuration.configure(null, new File("proguard-rules.pro")))
                .isInstanceOf(NullPointerException.class);
    }

    @FunctionalInterface
    private interface JarConfiguration {
        void configure(ProGuardTask task, Object file) throws Exception;
    }

    @FunctionalInterface
    private interface FilteredJarConfiguration {
        void configure(ProGuardTask task, Map<?, ?> filters, Object file) throws Exception;
    }
}
