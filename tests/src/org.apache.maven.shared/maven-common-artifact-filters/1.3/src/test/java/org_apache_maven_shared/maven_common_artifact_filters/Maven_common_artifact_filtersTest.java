/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_common_artifact_filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.ScopeArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.junit.jupiter.api.Test;

public class Maven_common_artifact_filtersTest {
    @Test
    void patternIncludesSupportNegativeRulesWildcardsVersionRangesAndTransitiveTrails() {
        Artifact allowed = artifact("org.example", "api", "1.1", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact blocked = artifact("org.example", "blocked", "1.1", Artifact.SCOPE_COMPILE, "jar", null);
        PatternIncludesArtifactFilter negativeOnlyFilter = new PatternIncludesArtifactFilter(
                Arrays.asList("!org.example:blocked"));

        assertThat(negativeOnlyFilter.include(allowed)).isTrue();
        assertThat(negativeOnlyFilter.include(blocked)).isFalse();
        assertThat(negativeOnlyFilter.hasMissedCriteria()).isFalse();
        assertThat(negativeOnlyFilter.toString()).contains("Includes filter:");

        Artifact current = artifact("com.acme", "current", "1.5", Artifact.SCOPE_RUNTIME, "jar", null);
        Artifact old = artifact("com.acme", "old", "0.9", Artifact.SCOPE_RUNTIME, "jar", null);
        PatternIncludesArtifactFilter versionRangeFilter = new PatternIncludesArtifactFilter(
                Arrays.asList("com.acme:*:jar:[1.0,2.0)"));

        assertThat(versionRangeFilter.include(current)).isTrue();
        assertThat(versionRangeFilter.include(old)).isFalse();

        Artifact transitive = artifact("org.other", "leaf", "3.0", Artifact.SCOPE_RUNTIME, "jar", null);
        transitive.setDependencyTrail(Arrays.asList(
                "com.acme:root:jar:1.0",
                "com.acme:parent:jar:1.0",
                transitive.getId()));
        PatternIncludesArtifactFilter transitiveFilter = new PatternIncludesArtifactFilter(
                Arrays.asList("com.acme:parent"), true);

        assertThat(transitiveFilter.include(transitive)).isTrue();
    }

    @Test
    void patternExcludesInvertMatchesAndTrackMissedCriteria() {
        Artifact blocked = artifact("org.example", "blocked-core", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact allowed = artifact("org.example", "api", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        PatternExcludesArtifactFilter filter = new PatternExcludesArtifactFilter(Arrays.asList("*:blocked-*", "unused"));

        assertThat(filter.include(blocked)).isFalse();
        assertThat(filter.include(allowed)).isTrue();
        assertThat(filter.hasMissedCriteria()).isTrue();
        assertThat(filter.toString()).contains("Excludes filter:", "blocked-*");
    }

    @Test
    void strictPatternFiltersMatchArtifactCoordinatesExactlyWithOptionalSegments() {
        Artifact currentJar = artifact("com.acme.tools", "runner", "1.5", Artifact.SCOPE_RUNTIME, "jar", null);
        Artifact futureJar = artifact("com.acme.tools", "runner", "2.0", Artifact.SCOPE_RUNTIME, "jar", null);
        Artifact sources = artifact("com.acme.tools", "runner", "1.5", Artifact.SCOPE_RUNTIME, "java-source", "sources");

        StrictPatternIncludesArtifactFilter includes = new StrictPatternIncludesArtifactFilter(
                Arrays.asList("com.acme.*:runner:jar:[1.0,2.0)"));
        StrictPatternExcludesArtifactFilter excludes = new StrictPatternExcludesArtifactFilter(
                Arrays.asList("*:runner:java-*"));

        assertThat(includes.include(currentJar)).isTrue();
        assertThat(includes.include(futureJar)).isFalse();
        assertThat(includes.include(sources)).isFalse();
        assertThat(excludes.include(currentJar)).isTrue();
        assertThat(excludes.include(sources)).isFalse();
    }

    @Test
    void scopeArtifactFilterSupportsImpliedScopesFineGrainedFlagsAndMissedCriteriaTracking() {
        Artifact nullScope = artifact("org.example", "no-scope", "1.0", null, "jar", null);
        Artifact compile = artifact("org.example", "compile", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact runtime = artifact("org.example", "runtime", "1.0", Artifact.SCOPE_RUNTIME, "jar", null);
        Artifact test = artifact("org.example", "test", "1.0", Artifact.SCOPE_TEST, "jar", null);
        Artifact provided = artifact("org.example", "provided", "1.0", Artifact.SCOPE_PROVIDED, "jar", null);
        Artifact system = artifact("org.example", "system", "1.0", Artifact.SCOPE_SYSTEM, "jar", null);

        ScopeArtifactFilter runtimeFilter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
        assertThat(runtimeFilter.include(compile)).isTrue();
        assertThat(runtimeFilter.include(runtime)).isTrue();
        assertThat(runtimeFilter.include(test)).isFalse();
        assertThat(runtimeFilter.include(provided)).isFalse();
        assertThat(runtimeFilter.toString()).contains("compile=true", "runtime=true", "test=false");

        ScopeArtifactFilter customFilter = new ScopeArtifactFilter()
                .setIncludeTestScopeWithImplications(true)
                .setIncludeNullScope(true);
        assertThat(customFilter.include(nullScope)).isTrue();
        assertThat(customFilter.include(compile)).isTrue();
        assertThat(customFilter.include(runtime)).isTrue();
        assertThat(customFilter.include(test)).isTrue();
        assertThat(customFilter.include(provided)).isTrue();
        assertThat(customFilter.include(system)).isTrue();
        assertThat(customFilter.hasMissedCriteria()).isFalse();
        assertThat(customFilter.reset()).isSameAs(customFilter);
        assertThat(customFilter.hasMissedCriteria()).isTrue();

        ScopeArtifactFilter nullRejectingFilter = new ScopeArtifactFilter().setIncludeNullScope(false);
        assertThat(nullRejectingFilter.include(nullScope)).isFalse();
    }

    @Test
    void collectionFeatureFiltersApplyIncludesExcludesAndCanBeChained() throws ArtifactFilterException {
        Artifact core = artifact("com.acme", "core", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact tests = artifact("com.acme", "core-tests", "1.0", Artifact.SCOPE_TEST, "test-jar", "tests");
        Artifact model = artifact("com.acme.model", "model", "1.0", Artifact.SCOPE_COMPILE, "pom", null);
        Artifact foreign = artifact("org.other", "core", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Set<Artifact> artifacts = artifactSet(core, tests, model, foreign);

        GroupIdFilter groupIdFilter = new GroupIdFilter("com.acme", null);
        assertThat(groupIdFilter.filter(artifacts)).containsExactlyInAnyOrder(core, tests, model);
        assertThat(groupIdFilter.getIncludes()).containsExactly("com.acme");

        ArtifactIdFilter artifactIdFilter = new ArtifactIdFilter(null, "core-tests");
        assertThat(artifactIdFilter.filter(artifacts)).containsExactlyInAnyOrder(core, model, foreign);
        assertThat(artifactIdFilter.isArtifactIncluded(tests)).isFalse();

        TypeFilter typeFilter = new TypeFilter("jar,test-jar", "pom");
        ClassifierFilter classifierFilter = new ClassifierFilter("tests", null);
        FilterArtifacts chain = new FilterArtifacts();
        chain.addFilter(typeFilter);
        chain.addFilter(0, classifierFilter);
        chain.addFilter(null);

        assertThat(chain.getFilters()).containsExactly(classifierFilter, typeFilter);
        assertThat(chain.filter(artifacts)).containsExactly(tests);

        chain.clearFilters();
        assertThat(chain.getFilters()).isEmpty();
        assertThat(chain.filter(artifacts)).containsExactlyInAnyOrderElementsOf(artifacts);
    }

    @Test
    void collectionScopeFilterIncludesOrExcludesMavenScopesAndRejectsInvalidInput() throws ArtifactFilterException {
        Artifact compile = artifact("org.example", "compile", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact runtime = artifact("org.example", "runtime", "1.0", Artifact.SCOPE_RUNTIME, "jar", null);
        Artifact test = artifact("org.example", "test", "1.0", Artifact.SCOPE_TEST, "jar", null);
        Artifact provided = artifact("org.example", "provided", "1.0", Artifact.SCOPE_PROVIDED, "jar", null);
        Artifact system = artifact("org.example", "system", "1.0", Artifact.SCOPE_SYSTEM, "jar", null);
        Set<Artifact> artifacts = artifactSet(compile, runtime, test, provided, system);

        ScopeFilter includeRuntime = new ScopeFilter(Artifact.SCOPE_RUNTIME, null);
        assertThat(includeRuntime.filter(artifacts)).containsExactlyInAnyOrder(compile, runtime);
        assertThat(includeRuntime.getIncludeScope()).isEqualTo(Artifact.SCOPE_RUNTIME);

        includeRuntime.setIncludeScope(Artifact.SCOPE_PROVIDED);
        assertThat(includeRuntime.filter(artifacts)).containsExactly(provided);

        ScopeFilter excludeProvided = new ScopeFilter(null, Artifact.SCOPE_PROVIDED);
        assertThat(excludeProvided.filter(artifacts)).containsExactlyInAnyOrder(compile, runtime, test, system);
        assertThat(excludeProvided.getExcludeScope()).isEqualTo(Artifact.SCOPE_PROVIDED);

        excludeProvided.setExcludeScope(Artifact.SCOPE_TEST);
        assertThatThrownBy(() -> excludeProvided.filter(artifacts))
                .isInstanceOf(ArtifactFilterException.class)
                .hasMessageContaining("Can't exclude Test scope");

        ScopeFilter invalidInclude = new ScopeFilter("custom", null);
        assertThatThrownBy(() -> invalidInclude.filter(artifacts))
                .isInstanceOf(ArtifactFilterException.class)
                .hasMessageContaining("Invalid Scope in includeScope");
    }

    @Test
    void projectTransitivityFilterKeepsOnlyDirectDependenciesWhenEnabled() {
        Artifact direct = artifact("org.example", "direct", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        Artifact transitive = artifact("org.example", "transitive", "1.0", Artifact.SCOPE_RUNTIME, "jar", null);
        Set<Artifact> directDependencies = artifactSet(direct);
        Set<Artifact> allArtifacts = artifactSet(direct, transitive);
        ProjectTransitivityFilter filter = new ProjectTransitivityFilter(directDependencies, true);

        assertThat(filter.isExcludeTransitive()).isTrue();
        assertThat(filter.artifactIsADirectDependency(direct)).isTrue();
        assertThat(filter.artifactIsADirectDependency(transitive)).isFalse();
        assertThat(filter.filter(allArtifacts)).containsExactly(direct);

        filter.setExcludeTransitive(false);
        assertThat(filter.filter(allArtifacts)).containsExactlyInAnyOrder(direct, transitive);
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String type,
            String classifier) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), scope, type, classifier,
                handler);
    }

    private static Set<Artifact> artifactSet(Artifact... artifacts) {
        return new LinkedHashSet<>(Arrays.asList(artifacts));
    }
}
