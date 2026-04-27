/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_spotbugs.spotbugs_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.Confidence;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;
import edu.umd.cs.findbugs.annotations.ReturnValuesAreNonnullByDefault;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import edu.umd.cs.findbugs.annotations.UnknownNullness;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Spotbugs_annotationsTest {
    @Test
    void enumsExposeStableOrderingAndBoundaryMappings() {
        assertThat(Confidence.values()).containsExactly(
                Confidence.HIGH,
                Confidence.MEDIUM,
                Confidence.LOW,
                Confidence.IGNORE);
        assertThat(Confidence.valueOf("MEDIUM")).isSameAs(Confidence.MEDIUM);
        assertThat(Confidence.HIGH.getConfidenceValue()).isEqualTo(1);
        assertThat(Confidence.MEDIUM.getConfidenceValue()).isEqualTo(2);
        assertThat(Confidence.LOW.getConfidenceValue()).isEqualTo(3);
        assertThat(Confidence.IGNORE.getConfidenceValue()).isEqualTo(5);
        assertThat(Confidence.getConfidence(Integer.MIN_VALUE)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(-1)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(0)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(1)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(2)).isSameAs(Confidence.MEDIUM);
        assertThat(Confidence.getConfidence(3)).isSameAs(Confidence.LOW);
        assertThat(Confidence.getConfidence(4)).isSameAs(Confidence.IGNORE);
        assertThat(Confidence.getConfidence(100)).isSameAs(Confidence.IGNORE);

    }

    @Test
    void manualAnnotationImplementationsExposeMembersTypesAndDefaults() throws NoSuchMethodException {
        CheckForNull checkForNull = new CheckForNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckForNull.class;
            }
        };
        NonNull nonNull = new NonNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NonNull.class;
            }
        };
        Nullable nullable = new Nullable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
        UnknownNullness unknownNullness = new UnknownNullness() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return UnknownNullness.class;
            }
        };
        CleanupObligation cleanupObligation = new CleanupObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CleanupObligation.class;
            }
        };
        CreatesObligation createsObligation = new CreatesObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CreatesObligation.class;
            }
        };
        DischargesObligation dischargesObligation = new DischargesObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return DischargesObligation.class;
            }
        };
        ReturnValuesAreNonnullByDefault returnValuesAreNonnullByDefault = new ReturnValuesAreNonnullByDefault() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ReturnValuesAreNonnullByDefault.class;
            }
        };
        SuppressFBWarnings suppressFBWarnings = new SuppressFBWarnings() {
            @Override
            public String[] value() {
                return new String[] {"NP_NULL_ON_SOME_PATH", "UWF_UNWRITTEN_FIELD"};
            }

            @Override
            public String justification() {
                return "Verified by the test fixture";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return SuppressFBWarnings.class;
            }
        };

        assertThat(checkForNull.annotationType()).isSameAs(CheckForNull.class);
        assertThat(nonNull.annotationType()).isSameAs(NonNull.class);
        assertThat(nullable.annotationType()).isSameAs(Nullable.class);
        assertThat(unknownNullness.annotationType()).isSameAs(UnknownNullness.class);
        assertThat(cleanupObligation.annotationType()).isSameAs(CleanupObligation.class);
        assertThat(createsObligation.annotationType()).isSameAs(CreatesObligation.class);
        assertThat(dischargesObligation.annotationType()).isSameAs(DischargesObligation.class);
        assertThat(returnValuesAreNonnullByDefault.annotationType()).isSameAs(ReturnValuesAreNonnullByDefault.class);

        assertThat(suppressFBWarnings.value()).containsExactly("NP_NULL_ON_SOME_PATH", "UWF_UNWRITTEN_FIELD");
        assertThat(suppressFBWarnings.justification()).isEqualTo("Verified by the test fixture");
        assertThat(suppressFBWarnings.annotationType()).isSameAs(SuppressFBWarnings.class);

        assertThat(OverrideMustInvoke.class.getDeclaredMethod("value").getReturnType().getName())
                .isEqualTo("edu.umd.cs.findbugs.annotations.When");
        assertThat(String.valueOf(OverrideMustInvoke.class.getDeclaredMethod("value").getDefaultValue()))
                .isEqualTo("ANYTIME");
        assertThat((String[]) SuppressFBWarnings.class.getDeclaredMethod("value").getDefaultValue()).isEmpty();
        assertThat(SuppressFBWarnings.class.getDeclaredMethod("justification").getDefaultValue()).isEqualTo("");
        assertThat(SuppressFBWarnings.class.getAnnotationsByType(Retention.class)[0].value())
                .isEqualTo(RetentionPolicy.CLASS);
        assertThat(SuppressFBWarnings.class.getAnnotationsByType(Target.class)).isEmpty();
    }

    @Test
    void nullnessAnnotationsExposeMetaAnnotationsAndAnnotatedCodePathsExecuteNormally()
            throws NoSuchFieldException, NoSuchMethodException {
        NullnessFixture fixture = new NullnessFixture("  Primary Owner  ", null);

        assertThat(fixture.requiredName()).isEqualTo("primary owner");
        assertThat(fixture.optionalAlias(false)).isNull();
        assertThat(fixture.optionalAlias(true)).isNull();
        assertThat(fixture.labelFor("  Team North  ")).isEqualTo("team north");
        assertThat(fixture.labelFor(null)).isEqualTo("primary owner");
        assertThat(fixture.normalizedDisplayName()).isEqualTo("primary owner");
        fixture.closeResourcesInOrder();
        assertThat(fixture.wasClosed()).isTrue();

        assertThat(CheckForNull.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(CheckForNull.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertThat(CheckForNull.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(CheckForNull.class.getAnnotationsByType(Nonnull.class)[0].when())
                .isEqualTo(javax.annotation.meta.When.MAYBE);

        assertThat(NonNull.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(NonNull.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertThat(NonNull.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(NonNull.class.getAnnotationsByType(Nonnull.class)[0].when())
                .isEqualTo(javax.annotation.meta.When.ALWAYS);

        assertThat(Nullable.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(Nullable.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertThat(Nullable.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(Nullable.class.getAnnotationsByType(Nonnull.class)[0].when())
                .isEqualTo(javax.annotation.meta.When.UNKNOWN);

        assertThat(UnknownNullness.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(UnknownNullness.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertThat(UnknownNullness.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(UnknownNullness.class.getAnnotationsByType(Nonnull.class)[0].when())
                .isEqualTo(javax.annotation.meta.When.UNKNOWN);

        Constructor<NullnessFixture> constructor = NullnessFixture.class.getDeclaredConstructor(String.class, String.class);
        Method labelFor = NullnessFixture.class.getDeclaredMethod("labelFor", String.class);
        Method normalizedDisplayName = NullnessFixture.class.getDeclaredMethod("normalizedDisplayName");
        Method closeResourcesInOrder = NullnessFixture.class.getDeclaredMethod("closeResourcesInOrder");

        assertThat(NullnessFixture.class.getDeclaredField("requiredName").getDeclaredAnnotationsByType(NonNull.class))
                .isEmpty();
        assertThat(NullnessFixture.class.getDeclaredField("optionalAlias").getDeclaredAnnotationsByType(CheckForNull.class))
                .isEmpty();
        assertThat(constructor.getParameters()[0].getDeclaredAnnotationsByType(NonNull.class)).isEmpty();
        assertThat(constructor.getParameters()[1].getDeclaredAnnotationsByType(Nullable.class)).isEmpty();
        assertThat(labelFor.getDeclaredAnnotationsByType(UnknownNullness.class)).isEmpty();
        assertThat(labelFor.getParameters()[0].getDeclaredAnnotationsByType(UnknownNullness.class)).isEmpty();
        assertThat(normalizedDisplayName.getDeclaredAnnotationsByType(SuppressFBWarnings.class)).isEmpty();
        assertThat(closeResourcesInOrder.getDeclaredAnnotationsByType(OverrideMustInvoke.class)).isEmpty();
    }

    @Test
    void runtimeRetainedAnnotationsRemainVisibleAndSupportLifecycleCode() throws NoSuchMethodException {
        ManagedResource parent = new ManagedResource("parent");
        ManagedResource child = parent.openChild("child");

        assertThat(parent.describe()).isEqualTo("parent:open");
        assertThat(child.describe()).isEqualTo("parent/child:open");
        assertThat(parent.isClosed()).isFalse();
        assertThat(child.isClosed()).isFalse();

        child.close();
        parent.close();

        assertThat(child.isClosed()).isTrue();
        assertThat(parent.isClosed()).isTrue();
        assertThat(parent.describe()).isEqualTo("parent:closed");
        assertThat(child.describe()).isEqualTo("parent/child:closed");

        Constructor<ManagedResource> constructor = ManagedResource.class.getDeclaredConstructor(String.class);
        Method openChild = ManagedResource.class.getDeclaredMethod("openChild", String.class);
        Method close = ManagedResource.class.getDeclaredMethod("close");

        assertThat(ManagedResource.class.getAnnotationsByType(CleanupObligation.class)).hasSize(1);
        assertThat(ManagedResource.class.getAnnotationsByType(ReturnValuesAreNonnullByDefault.class)).hasSize(1);
        assertThat(constructor.getAnnotationsByType(CreatesObligation.class)).hasSize(1);
        assertThat(openChild.getAnnotationsByType(CreatesObligation.class)).hasSize(1);
        assertThat(close.getAnnotationsByType(DischargesObligation.class)).hasSize(1);

        assertThat(CleanupObligation.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(CleanupObligation.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(CleanupObligation.class.getAnnotationsByType(Target.class)[0].value()).containsExactly(ElementType.TYPE);

        assertThat(CreatesObligation.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(CreatesObligation.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(CreatesObligation.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.METHOD, ElementType.CONSTRUCTOR);

        assertThat(DischargesObligation.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(DischargesObligation.class.getAnnotationsByType(Retention.class)[0].value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DischargesObligation.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.METHOD);

        assertThat(ReturnValuesAreNonnullByDefault.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(ReturnValuesAreNonnullByDefault.class.getAnnotationsByType(Nonnull.class)).hasSize(1);
        assertThat(ReturnValuesAreNonnullByDefault.class.getAnnotationsByType(TypeQualifierDefault.class)[0].value())
                .containsExactly(ElementType.METHOD);
        assertThat(ReturnValuesAreNonnullByDefault.class.getAnnotationsByType(Retention.class)[0].value())
                .isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void checkReturnValueAnnotationsExposeDefaultsAndAnnotatedResultsCanBeComposed()
            throws NoSuchMethodException {
        CoordinateSummary summary = new CoordinateSummary(" com.github.spotbugs ", " spotbugs-annotations ");
        CoordinateSummary documentedSummary = summary.withCapability(" Nullness ");
        CoordinateSummary nativeSummary = documentedSummary.withCapability(" Native Image ");

        assertThat(summary.coordinate()).isEqualTo("com.github.spotbugs:spotbugs-annotations");
        assertThat(documentedSummary.coordinate()).isEqualTo("com.github.spotbugs:spotbugs-annotations#nullness");
        assertThat(nativeSummary.coordinate()).isEqualTo("com.github.spotbugs:spotbugs-annotations#nullness,native image");
        assertThat(summary.hasCapability("nullness")).isFalse();
        assertThat(nativeSummary.hasCapability(" NULLNESS ")).isTrue();
        assertThat(nativeSummary.hasCapability("native-image")).isTrue();
        assertThat(nativeSummary.capabilityCount()).isEqualTo(2);

        Constructor<CoordinateSummary> constructor = CoordinateSummary.class.getDeclaredConstructor(String.class, String.class);
        Method withCapability = CoordinateSummary.class.getDeclaredMethod("withCapability", String.class);
        Method coordinate = CoordinateSummary.class.getDeclaredMethod("coordinate");
        Method hasCapability = CoordinateSummary.class.getDeclaredMethod("hasCapability", String.class);

        assertThat(constructor.getDeclaredAnnotationsByType(CheckReturnValue.class)).isEmpty();
        assertThat(withCapability.getDeclaredAnnotationsByType(CheckReturnValue.class)).isEmpty();
        assertThat(coordinate.getDeclaredAnnotationsByType(CheckReturnValue.class)).isEmpty();
        assertThat(hasCapability.getDeclaredAnnotationsByType(CheckReturnValue.class)).isEmpty();

        assertThat(CheckReturnValue.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(CheckReturnValue.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactly(ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertThat(CheckReturnValue.class.getAnnotationsByType(Retention.class)[0].value())
                .isEqualTo(RetentionPolicy.CLASS);
        assertThat(CheckReturnValue.class.getDeclaredMethod("confidence").getReturnType()).isSameAs(Confidence.class);
        assertThat(CheckReturnValue.class.getDeclaredMethod("confidence").getDefaultValue())
                .isSameAs(Confidence.MEDIUM);
        assertThat(CheckReturnValue.class.getDeclaredMethod("explanation").getDefaultValue()).isEqualTo("");
    }

    @Test
    void runtimeObligationAnnotationsMustBeRedeclaredForSubclassesAndOverrides() throws NoSuchMethodException {
        ImplicitObligationResource inherited = new ImplicitObligationResource("inherited");
        ExplicitObligationResource explicit = new ExplicitObligationResource("explicit");

        assertThat(inherited.status()).isEqualTo("inherited:open");
        assertThat(explicit.status()).isEqualTo("explicit:open");

        inherited.release();
        explicit.release();

        assertThat(inherited.status()).isEqualTo("inherited:closed");
        assertThat(explicit.status()).isEqualTo("explicit:closed");

        CleanupObligation baseCleanup = annotation(BaseObligationResource.class, CleanupObligation.class);
        CleanupObligation explicitCleanup = annotation(ExplicitObligationResource.class, CleanupObligation.class);
        ReturnValuesAreNonnullByDefault baseDefault =
                annotation(BaseObligationResource.class, ReturnValuesAreNonnullByDefault.class);
        ReturnValuesAreNonnullByDefault explicitDefault =
                annotation(ExplicitObligationResource.class, ReturnValuesAreNonnullByDefault.class);

        assertThat(baseCleanup).isNotNull();
        assertThat(explicitCleanup).isNotNull();
        assertThat(baseCleanup).isEqualTo(explicitCleanup);
        assertThat(baseCleanup.hashCode()).isEqualTo(explicitCleanup.hashCode());
        assertThat(baseDefault).isNotNull();
        assertThat(explicitDefault).isNotNull();
        assertThat(baseDefault).isEqualTo(explicitDefault);
        assertThat(baseDefault.hashCode()).isEqualTo(explicitDefault.hashCode());

        assertThat(annotation(ImplicitObligationResource.class, CleanupObligation.class)).isNull();
        assertThat(annotation(ImplicitObligationResource.class, ReturnValuesAreNonnullByDefault.class)).isNull();

        Constructor<ImplicitObligationResource> inheritedConstructor =
                ImplicitObligationResource.class.getDeclaredConstructor(String.class);
        Constructor<ExplicitObligationResource> explicitConstructor =
                ExplicitObligationResource.class.getDeclaredConstructor(String.class);
        Method inheritedRelease = ImplicitObligationResource.class.getDeclaredMethod("release");
        Method explicitRelease = ExplicitObligationResource.class.getDeclaredMethod("release");

        assertThat(annotation(inheritedConstructor, CreatesObligation.class)).isNull();
        assertThat(annotation(explicitConstructor, CreatesObligation.class)).isNotNull();
        assertThat(annotation(inheritedRelease, DischargesObligation.class)).isNull();
        assertThat(annotation(explicitRelease, DischargesObligation.class)).isNotNull();
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        A[] annotations = element.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }
    // Checkstyle: disallow direct annotation access

    @ReturnValuesAreNonnullByDefault
    private static final class NullnessFixture {
        @NonNull
        private final String requiredName;

        @CheckForNull
        private final String optionalAlias;

        private boolean closed;

        private NullnessFixture(@NonNull String requiredName, @Nullable String optionalAlias) {
            this.requiredName = normalize(requiredName);
            this.optionalAlias = normalizeNullable(optionalAlias);
        }

        @NonNull
        private String requiredName() {
            return requiredName;
        }

        @CheckForNull
        private String optionalAlias(boolean includeAlias) {
            return includeAlias ? optionalAlias : null;
        }

        @UnknownNullness
        private String labelFor(@UnknownNullness String candidate) {
            @Nullable String normalizedCandidate = normalizeNullable(candidate);
            return normalizedCandidate == null ? requiredName : normalizedCandidate;
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "Intentional null fallback path")
        private String normalizedDisplayName() {
            @CheckForNull String preferredLabel = optionalAlias(true);
            return preferredLabel == null ? requiredName : preferredLabel;
        }

        @OverrideMustInvoke
        private void closeResourcesInOrder() {
            closed = true;
        }

        private boolean wasClosed() {
            return closed;
        }

        private static String normalize(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }

        private static String normalizeNullable(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return normalized.isEmpty() ? null : normalized;
        }
    }

    private static final class CoordinateSummary {
        private final String groupId;
        private final String artifactId;
        private final String capabilities;

        @CheckReturnValue(explanation = "Use the constructed summary to inspect the normalized coordinate")
        private CoordinateSummary(String groupId, String artifactId) {
            this(normalizeSegment(groupId), normalizeSegment(artifactId), "");
        }

        private CoordinateSummary(String groupId, String artifactId, String capabilities) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.capabilities = capabilities;
        }

        @CheckReturnValue(confidence = Confidence.HIGH,
                explanation = "Use the returned summary to retain the appended capability")
        private CoordinateSummary withCapability(String capability) {
            String normalizedCapability = normalizeSegment(capability).replace('-', ' ');
            String updatedCapabilities = capabilities.isEmpty()
                    ? normalizedCapability
                    : capabilities + "," + normalizedCapability;
            return new CoordinateSummary(groupId, artifactId, updatedCapabilities);
        }

        @CheckReturnValue(confidence = Confidence.HIGH, explanation = "Use the rendered coordinate string")
        private String coordinate() {
            if (capabilities.isEmpty()) {
                return groupId + ":" + artifactId;
            }
            return groupId + ":" + artifactId + "#" + capabilities;
        }

        @CheckReturnValue(explanation = "Use the membership result")
        private boolean hasCapability(String capability) {
            if (capabilities.isEmpty()) {
                return false;
            }
            String normalizedCapability = normalizeSegment(capability).replace('-', ' ');
            for (String existingCapability : capabilities.split(",")) {
                if (existingCapability.equals(normalizedCapability)) {
                    return true;
                }
            }
            return false;
        }

        private int capabilityCount() {
            return capabilities.isEmpty() ? 0 : capabilities.split(",").length;
        }

        private static String normalizeSegment(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }

    @CleanupObligation
    @ReturnValuesAreNonnullByDefault
    private static class BaseObligationResource {
        private final String name;
        private boolean released;

        @CreatesObligation
        private BaseObligationResource(String name) {
            this.name = name;
        }

        String status() {
            return name + ":" + (released ? "closed" : "open");
        }

        @DischargesObligation
        void release() {
            released = true;
        }
    }

    private static final class ImplicitObligationResource extends BaseObligationResource {
        private ImplicitObligationResource(String name) {
            super(name);
        }

        @Override
        void release() {
            super.release();
        }
    }

    @CleanupObligation
    @ReturnValuesAreNonnullByDefault
    private static final class ExplicitObligationResource extends BaseObligationResource {
        @CreatesObligation
        private ExplicitObligationResource(String name) {
            super(name);
        }

        @Override
        @DischargesObligation
        void release() {
            super.release();
        }
    }

    @CleanupObligation
    @ReturnValuesAreNonnullByDefault
    private static final class ManagedResource {
        private final String name;
        private boolean closed;

        @CreatesObligation
        private ManagedResource(String name) {
            this.name = name;
        }

        @CreatesObligation
        private ManagedResource openChild(String childName) {
            return new ManagedResource(name + "/" + childName);
        }

        @DischargesObligation
        private void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }

        private String describe() {
            return name + ":" + (closed ? "closed" : "open");
        }
    }
}
