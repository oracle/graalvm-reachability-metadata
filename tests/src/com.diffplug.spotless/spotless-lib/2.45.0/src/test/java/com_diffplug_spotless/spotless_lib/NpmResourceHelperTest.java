/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.npm.NpmPathResolver;
import com.diffplug.spotless.npm.PrettierConfig;
import com.diffplug.spotless.npm.PrettierFormatterStep;

public class NpmResourceHelperTest {
    private static final Provisioner UNUSED_PROVISIONER = (withTransitives, mavenCoordinates) -> Set.of();

    @Test
    void loadsBundledPrettierResourcesWhenLazyStepStateIsCalculated() throws Exception {
        final Path projectDir = Files.createTempDirectory("spotless-npm-project-");
        final Path buildDir = Files.createTempDirectory("spotless-npm-build-");
        final Path cacheDir = Files.createTempDirectory("spotless-npm-cache-");

        final FormatterStep firstStep = createPrettierStep(projectDir, buildDir, cacheDir);
        final FormatterStep secondStep = createPrettierStep(projectDir, buildDir, cacheDir);

        assertThat(firstStep.getName()).isEqualTo(PrettierFormatterStep.NAME);
        assertThat(firstStep).hasSameHashCodeAs(secondStep);
    }

    private static FormatterStep createPrettierStep(Path projectDir, Path buildDir, Path cacheDir) {
        return PrettierFormatterStep.create(
                PrettierFormatterStep.defaultDevDependencies(),
                UNUSED_PROVISIONER,
                projectDir.toFile(),
                buildDir.toFile(),
                cacheDir.toFile(),
                new NpmPathResolver(null, null, null, List.of()),
                new PrettierConfig(null, null));
    }
}
