/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.java.GoogleJavaFormatStep;

public class ModuleHelperTest {
    @Test
    void googleJavaFormatStepInitializesModuleSupportWhenLazyStateIsMaterialized() throws Exception {
        final FixtureProvisioner provisioner = new FixtureProvisioner();
        final FormatterStep step = GoogleJavaFormatStep.create(GoogleJavaFormatStep.defaultVersion(), provisioner);

        final int firstHashCode = step.hashCode();
        final int secondHashCode = step.hashCode();

        assertThat(secondHashCode).isEqualTo(firstHashCode);
        assertThat(step.getName()).isEqualTo("google-java-format");
        assertThat(provisioner.withTransitives()).isTrue();
        assertThat(provisioner.mavenCoordinates()).containsExactly(
                GoogleJavaFormatStep.defaultGroupArtifact() + ":" + GoogleJavaFormatStep.defaultVersion());
    }

    private static final class FixtureProvisioner implements Provisioner {
        private final Set<File> fixtureJars;
        private boolean withTransitives;
        private Collection<String> mavenCoordinates = Set.of();

        private FixtureProvisioner() throws IOException {
            fixtureJars = Set.of(createFixtureJar());
        }

        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            this.withTransitives = withTransitives;
            this.mavenCoordinates = mavenCoordinates;
            return new LinkedHashSet<>(fixtureJars);
        }

        private boolean withTransitives() {
            return withTransitives;
        }

        private Collection<String> mavenCoordinates() {
            return mavenCoordinates;
        }

        private static File createFixtureJar() throws IOException {
            final Path jarFile = Files.createTempFile("google-java-format-fixture-", ".jar");
            try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
                final JarEntry entry = new JarEntry("fixture.txt");
                outputStream.putNextEntry(entry);
                outputStream.write("module helper fixture".getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
            jarFile.toFile().deleteOnExit();
            return jarFile.toFile();
        }
    }
}
