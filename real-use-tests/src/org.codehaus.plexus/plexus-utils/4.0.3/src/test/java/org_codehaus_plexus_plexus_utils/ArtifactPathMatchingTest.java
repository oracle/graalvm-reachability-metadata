/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.util.List;

import org.codehaus.plexus.util.MatchPatterns;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matches repository artifact paths with Plexus pattern utilities.
 *
 * A release indexer walks a repository and publishes only JAR and POM files from
 * release directories, while pruning directories that cannot contain matches.
 */
class ArtifactPathMatchingTest {

    @Test
    void selectsReleaseArtifactsDuringRepositoryWalk() {
        MatchPatterns artifactPatterns = MatchPatterns.from(
                "releases/**/*.jar", "releases/**/*.pom");
        List<String> repositoryPaths = List.of(
                "releases/catalog.jar",
                "releases/2025/catalog.pom",
                "releases/2025/catalog-sources.jar",
                "snapshots/catalog.jar",
                "releases/2025/catalog.zip");

        List<String> publishedArtifacts = repositoryPaths.stream()
                .filter(path -> artifactPatterns.matches(path, true))
                .toList();

        assertThat(artifactPatterns.getSources())
                .containsExactly("releases/**/*.jar", "releases/**/*.pom");
        assertThat(artifactPatterns.matchesPatternStart("releases/2025", true)).isTrue();
        assertThat(artifactPatterns.matchesPatternStart("snapshots", true)).isFalse();
        assertThat(publishedArtifacts).containsExactly(
                "releases/catalog.jar",
                "releases/2025/catalog.pom",
                "releases/2025/catalog-sources.jar");
    }
}
