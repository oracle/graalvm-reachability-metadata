/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.annotations.Consumer;
import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Generated;
import org.apache.maven.api.annotations.Immutable;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.NotThreadSafe;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.annotations.Provider;
import org.apache.maven.api.annotations.ThreadSafe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Maven_api_metaTest {
    @Test
    void annotationTypesCanBeReferencedFromExecutableCode() {
        List<Class<?>> annotationTypes = List.of(
                Consumer.class,
                Experimental.class,
                Generated.class,
                Immutable.class,
                Nonnull.class,
                NotThreadSafe.class,
                Nullable.class,
                Provider.class,
                ThreadSafe.class);

        assertThat(annotationTypes)
                .containsExactly(
                        Consumer.class,
                        Experimental.class,
                        Generated.class,
                        Immutable.class,
                        Nonnull.class,
                        NotThreadSafe.class,
                        Nullable.class,
                        Provider.class,
                        ThreadSafe.class)
                .doesNotHaveDuplicates();
    }

    @Test
    void mavenApiAnnotationsCanDescribeConsumerAndProviderContracts() {
        MavenExtension extension = new MavenExtension("compiler", "javac");
        MavenService service = new MavenService(extension);

        assertThat(service.extension().name()).isEqualTo("compiler");
        assertThat(service.describeExtension("language")).isEqualTo("compiler:language");
        assertThat(service.describeExtension(null)).isEqualTo("compiler");
        assertThat(service.serviceIds()).containsExactly("compiler", "javac");
    }

    @Test
    void nullableAnnotationsCanDescribeAbsentOptionalValues() {
        MavenExtension extension = new MavenExtension("resources", null);
        MavenService service = new MavenService(extension);

        assertThat(extension.implementationHint()).isNull();
        assertThat(service.describeExtension("native-image")).isEqualTo("resources:native-image");
        assertThat(service.serviceIds()).containsExactly("resources");
    }

    @Test
    void notThreadSafeAnnotationCanMarkMutableCollaborators() {
        InvocationLog invocationLog = new InvocationLog();

        invocationLog.add("validate");
        invocationLog.add("package");

        assertThat(invocationLog.entries()).containsExactly("validate", "package");
    }

    @Test
    void experimentalAnnotationCanDefineDomainSpecificApiMarkers() {
        PreviewGoalCatalog catalog = new PreviewGoalCatalog(List.of(
                new PreviewGoal("validate", false),
                new PreviewGoal("native-image", true),
                new PreviewGoal("deploy", false)));

        assertThat(catalog.namesRequiringNativeImage()).containsExactly("native-image");
        assertThat(catalog.namesAvailableOnJvm()).containsExactly("validate", "deploy");
    }

    @Test
    void experimentalAnnotationCanMarkIndividualPreviewOperations() {
        BuildLifecycle lifecycle = BuildLifecycle.withDefaults();

        assertThat(lifecycle.requiredPhase("java-compile")).isEqualTo("compile");
        assertThat(lifecycle.requiredPhase("native-build")).isEqualTo("validate");
        assertThat(lifecycle.requiredPhase("unknown-feature")).isEqualTo("validate");
        assertThat(lifecycle.previewPhase("native-build")).isEqualTo("native-image");
        assertThat(lifecycle.previewPhase("java-compile")).isNull();
        assertThat(lifecycle.previewPhase("unknown-feature")).isNull();
    }

    @Experimental
    @interface PreviewApi {
    }

    @Consumer
    interface ExtensionConsumer {
        @Nonnull
        String describeExtension(@Nullable String classifier);
    }

    @Provider
    @Experimental
    interface ExtensionProvider {
        @Nonnull
        MavenExtension extension();
    }

    @Generated
    @Immutable
    @ThreadSafe
    static final class MavenExtension {
        @Nonnull
        private final String name;

        @Nullable
        private final String implementationHint;

        MavenExtension(@Nonnull String name, @Nullable String implementationHint) {
            this.name = name;
            this.implementationHint = implementationHint;
        }

        @Nonnull
        String name() {
            return name;
        }

        @Nullable
        String implementationHint() {
            return implementationHint;
        }
    }

    @NotThreadSafe
    static final class MavenService implements ExtensionConsumer, ExtensionProvider {
        private final MavenExtension extension;

        MavenService(@Nonnull MavenExtension extension) {
            this.extension = extension;
        }

        @Override
        @Nonnull
        public MavenExtension extension() {
            return extension;
        }

        @Override
        @Nonnull
        public String describeExtension(@Nullable String classifier) {
            if (classifier == null || classifier.isBlank()) {
                return extension.name();
            }
            return extension.name() + ":" + classifier;
        }

        @Nonnull
        List<String> serviceIds() {
            List<String> ids = new ArrayList<>();
            ids.add(extension.name());
            @Nullable String implementationHint = extension.implementationHint();
            if (implementationHint != null) {
                ids.add(implementationHint);
            }
            return ids;
        }
    }

    @NotThreadSafe
    static final class InvocationLog {
        private final List<String> entries = new ArrayList<>();

        void add(@Nonnull String entry) {
            entries.add(entry);
        }

        @Nonnull
        List<String> entries() {
            return List.copyOf(entries);
        }
    }

    @PreviewApi
    @Immutable
    static final class PreviewGoal {
        @Nonnull
        private final String name;

        private final boolean requiresNativeImage;

        PreviewGoal(@Nonnull String name, boolean requiresNativeImage) {
            this.name = name;
            this.requiresNativeImage = requiresNativeImage;
        }

        @Nonnull
        String name() {
            return name;
        }

        boolean requiresNativeImage() {
            return requiresNativeImage;
        }
    }

    @Immutable
    static final class LifecyclePhase {
        @Nonnull
        private final String name;

        private final boolean preview;

        LifecyclePhase(@Nonnull String name, boolean preview) {
            this.name = name;
            this.preview = preview;
        }

        @Nonnull
        String name() {
            return name;
        }

        boolean preview() {
            return preview;
        }
    }

    @ThreadSafe
    static final class BuildLifecycle {
        private final Map<String, LifecyclePhase> phasesByFeature;

        @Nonnull
        private final String defaultPhase;

        private BuildLifecycle(@Nonnull Map<String, LifecyclePhase> phasesByFeature, @Nonnull String defaultPhase) {
            this.phasesByFeature = Map.copyOf(phasesByFeature);
            this.defaultPhase = defaultPhase;
        }

        @Nonnull
        static BuildLifecycle withDefaults() {
            Map<String, LifecyclePhase> phasesByFeature = new LinkedHashMap<>();
            phasesByFeature.put("java-compile", new LifecyclePhase("compile", false));
            phasesByFeature.put("native-build", new LifecyclePhase("native-image", true));
            return new BuildLifecycle(phasesByFeature, "validate");
        }

        @Nonnull
        String requiredPhase(@Nonnull String featureName) {
            LifecyclePhase phase = phasesByFeature.get(featureName);
            if (phase == null || phase.preview()) {
                return defaultPhase;
            }
            return phase.name();
        }

        @Experimental
        @Nullable
        String previewPhase(@Nonnull String featureName) {
            LifecyclePhase phase = phasesByFeature.get(featureName);
            if (phase == null || !phase.preview()) {
                return null;
            }
            return phase.name();
        }
    }

    @ThreadSafe
    static final class PreviewGoalCatalog {
        private final List<PreviewGoal> goals;

        PreviewGoalCatalog(@Nonnull List<PreviewGoal> goals) {
            this.goals = List.copyOf(goals);
        }

        @Nonnull
        List<String> namesRequiringNativeImage() {
            return namesByNativeImageRequirement(true);
        }

        @Nonnull
        List<String> namesAvailableOnJvm() {
            return namesByNativeImageRequirement(false);
        }

        @Nonnull
        private List<String> namesByNativeImageRequirement(boolean requiresNativeImage) {
            List<String> names = new ArrayList<>();
            for (PreviewGoal goal : goals) {
                if (goal.requiresNativeImage() == requiresNativeImage) {
                    names.add(goal.name());
                }
            }
            return List.copyOf(names);
        }
    }
}
