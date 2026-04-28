/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_findbugs.annotations;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.CheckReturnValue;
import javax.annotation.MatchesPattern;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import javax.annotation.RegEx;
import javax.annotation.meta.TypeQualifierValidator;

import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.Confidence;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForFields;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.DesireWarning;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import edu.umd.cs.findbugs.annotations.ExpectWarning;
import edu.umd.cs.findbugs.annotations.NoWarning;
import edu.umd.cs.findbugs.annotations.Priority;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsTest {
    @Test
    void confidenceAndMetaWhenEnumsExposeStableOrderingAndMappings() {
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
        assertThat(Confidence.getConfidence(5)).isSameAs(Confidence.IGNORE);
        assertThat(Confidence.getConfidence(Integer.MAX_VALUE)).isSameAs(Confidence.IGNORE);

        assertThat(javax.annotation.meta.When.values()).containsExactly(
                javax.annotation.meta.When.ALWAYS,
                javax.annotation.meta.When.UNKNOWN,
                javax.annotation.meta.When.MAYBE,
                javax.annotation.meta.When.NEVER);
        assertThat(javax.annotation.meta.When.valueOf("ALWAYS")).isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(javax.annotation.meta.When.valueOf("NEVER")).isSameAs(javax.annotation.meta.When.NEVER);
    }

    @Test
    void findbugsOverrideTimingEnumExposesStableOrderingAndLookup() {
        assertThat(edu.umd.cs.findbugs.annotations.When.values()).containsExactly(
                edu.umd.cs.findbugs.annotations.When.FIRST,
                edu.umd.cs.findbugs.annotations.When.ANYTIME,
                edu.umd.cs.findbugs.annotations.When.LAST);
        assertThat(edu.umd.cs.findbugs.annotations.When.valueOf("FIRST"))
                .isSameAs(edu.umd.cs.findbugs.annotations.When.FIRST);
        assertThat(edu.umd.cs.findbugs.annotations.When.valueOf("ANYTIME"))
                .isSameAs(edu.umd.cs.findbugs.annotations.When.ANYTIME);
        assertThat(edu.umd.cs.findbugs.annotations.When.valueOf("LAST"))
                .isSameAs(edu.umd.cs.findbugs.annotations.When.LAST);
    }

    @Test
    void publicAnnotationInterfacesExposeConfiguredMembersWithoutReflection() {
        CleanupObligation cleanupObligation = cleanupObligation();
        CreatesObligation createsObligation = createsObligation();
        DischargesObligation dischargesObligation = dischargesObligation();
        CheckForNull checkForNull = checkForNull();
        Nullable nullable = nullable();
        ParametersAreNonnullByDefault nonnullByDefault = parametersAreNonnullByDefault();
        ParametersAreNullableByDefault nullableByDefault = parametersAreNullableByDefault();
        OverridingMethodsMustInvokeSuper invokeSuper = overridingMethodsMustInvokeSuper();
        CheckReturnValue checkReturnValue = checkReturnValue(javax.annotation.meta.When.ALWAYS);
        Nonnull nonnull = nonnull(javax.annotation.meta.When.MAYBE);
        Nonnegative nonnegative = nonnegative(javax.annotation.meta.When.ALWAYS);
        DesireWarning desireWarning = desireWarning("NP", Confidence.MEDIUM, 7, 3);
        ExpectWarning expectWarning = expectWarning("UWF_UNWRITTEN_FIELD", Confidence.LOW, 9, 1);
        NoWarning noWarning = noWarning("DLS_DEAD_LOCAL_STORE", Confidence.HIGH, 4, 0);
        SuppressFBWarnings suppressFBWarnings = suppressFbWarnings(
                "Covered by direct API assertions",
                "NP_NULL_ON_SOME_PATH",
                "UWF_UNWRITTEN_FIELD");
        MatchesPattern matchesPattern = matchesPattern("[a-z]{3}\\d{2}", Pattern.CASE_INSENSITIVE);
        RegEx regex = regEx(javax.annotation.meta.When.UNKNOWN);
        net.jcip.annotations.GuardedBy jcipGuardedBy = jcipGuardedBy("stateLock");
        javax.annotation.concurrent.GuardedBy concurrentGuardedBy = concurrentGuardedBy("stateLock");

        assertThat(cleanupObligation.annotationType()).isSameAs(CleanupObligation.class);
        assertThat(createsObligation.annotationType()).isSameAs(CreatesObligation.class);
        assertThat(dischargesObligation.annotationType()).isSameAs(DischargesObligation.class);
        assertThat(checkForNull.annotationType()).isSameAs(CheckForNull.class);
        assertThat(nullable.annotationType()).isSameAs(Nullable.class);
        assertThat(nonnullByDefault.annotationType()).isSameAs(ParametersAreNonnullByDefault.class);
        assertThat(nullableByDefault.annotationType()).isSameAs(ParametersAreNullableByDefault.class);
        assertThat(invokeSuper.annotationType()).isSameAs(OverridingMethodsMustInvokeSuper.class);

        assertThat(checkReturnValue.when()).isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(checkReturnValue.annotationType()).isSameAs(CheckReturnValue.class);
        assertThat(nonnull.when()).isSameAs(javax.annotation.meta.When.MAYBE);
        assertThat(nonnull.annotationType()).isSameAs(Nonnull.class);
        assertThat(nonnegative.when()).isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(nonnegative.annotationType()).isSameAs(Nonnegative.class);

        assertThat(desireWarning.value()).isEqualTo("NP");
        assertThat(desireWarning.confidence()).isSameAs(Confidence.MEDIUM);
        assertThat(desireWarning.rank()).isEqualTo(7);
        assertThat(desireWarning.num()).isEqualTo(3);
        assertThat(desireWarning.annotationType()).isSameAs(DesireWarning.class);

        assertThat(expectWarning.value()).isEqualTo("UWF_UNWRITTEN_FIELD");
        assertThat(expectWarning.confidence()).isSameAs(Confidence.LOW);
        assertThat(expectWarning.rank()).isEqualTo(9);
        assertThat(expectWarning.num()).isEqualTo(1);
        assertThat(expectWarning.annotationType()).isSameAs(ExpectWarning.class);

        assertThat(noWarning.value()).isEqualTo("DLS_DEAD_LOCAL_STORE");
        assertThat(noWarning.confidence()).isSameAs(Confidence.HIGH);
        assertThat(noWarning.rank()).isEqualTo(4);
        assertThat(noWarning.num()).isZero();
        assertThat(noWarning.annotationType()).isSameAs(NoWarning.class);

        assertThat(suppressFBWarnings.value()).containsExactly("NP_NULL_ON_SOME_PATH", "UWF_UNWRITTEN_FIELD");
        assertThat(suppressFBWarnings.justification()).isEqualTo("Covered by direct API assertions");
        assertThat(suppressFBWarnings.annotationType()).isSameAs(SuppressFBWarnings.class);

        assertThat(matchesPattern.value()).isEqualTo("[a-z]{3}\\d{2}");
        assertThat(matchesPattern.flags()).isEqualTo(Pattern.CASE_INSENSITIVE);
        assertThat(matchesPattern.annotationType()).isSameAs(MatchesPattern.class);

        assertThat(regex.when()).isSameAs(javax.annotation.meta.When.UNKNOWN);
        assertThat(regex.annotationType()).isSameAs(RegEx.class);

        assertThat(jcipGuardedBy.value()).isEqualTo("stateLock");
        assertThat(jcipGuardedBy.annotationType()).isSameAs(net.jcip.annotations.GuardedBy.class);
        assertThat(concurrentGuardedBy.value()).isEqualTo("stateLock");
        assertThat(concurrentGuardedBy.annotationType()).isSameAs(javax.annotation.concurrent.GuardedBy.class);
    }

    @Test
    void defaultQualifierAnnotationsExposeConfiguredScopesAndPriorities() {
        DefaultAnnotation defaultAnnotation = defaultAnnotation(
                Priority.MEDIUM,
                Confidence.HIGH,
                annotationTypes(Nonnull.class, CheckReturnValue.class));
        DefaultAnnotationForFields defaultAnnotationForFields = defaultAnnotationForFields(
                Priority.HIGH,
                Confidence.MEDIUM,
                annotationTypes(CheckForNull.class, Nullable.class));
        DefaultAnnotationForMethods defaultAnnotationForMethods = defaultAnnotationForMethods(
                Priority.LOW,
                Confidence.LOW,
                annotationTypes(Nonnull.class, CheckReturnValue.class));
        DefaultAnnotationForParameters defaultAnnotationForParameters = defaultAnnotationForParameters(
                Priority.IGNORE,
                Confidence.IGNORE,
                annotationTypes(Nullable.class, CheckForNull.class));

        assertThat(defaultAnnotation.value()).containsExactly(Nonnull.class, CheckReturnValue.class);
        assertThat(defaultAnnotation.priority()).isSameAs(Priority.MEDIUM);
        assertThat(defaultAnnotation.confidence()).isSameAs(Confidence.HIGH);
        assertThat(defaultAnnotation.annotationType()).isSameAs(DefaultAnnotation.class);

        assertThat(defaultAnnotationForFields.value()).containsExactly(CheckForNull.class, Nullable.class);
        assertThat(defaultAnnotationForFields.priority()).isSameAs(Priority.HIGH);
        assertThat(defaultAnnotationForFields.confidence()).isSameAs(Confidence.MEDIUM);
        assertThat(defaultAnnotationForFields.annotationType()).isSameAs(DefaultAnnotationForFields.class);

        assertThat(defaultAnnotationForMethods.value()).containsExactly(Nonnull.class, CheckReturnValue.class);
        assertThat(defaultAnnotationForMethods.priority()).isSameAs(Priority.LOW);
        assertThat(defaultAnnotationForMethods.priority().getPriorityValue()).isEqualTo(3);
        assertThat(defaultAnnotationForMethods.confidence()).isSameAs(Confidence.LOW);
        assertThat(defaultAnnotationForMethods.annotationType()).isSameAs(DefaultAnnotationForMethods.class);

        assertThat(defaultAnnotationForParameters.value()).containsExactly(Nullable.class, CheckForNull.class);
        assertThat(defaultAnnotationForParameters.priority()).isSameAs(Priority.IGNORE);
        assertThat(defaultAnnotationForParameters.confidence()).isSameAs(Confidence.IGNORE);
        assertThat(defaultAnnotationForParameters.annotationType()).isSameAs(DefaultAnnotationForParameters.class);
    }

    @Test
    void javaxQualifierValidatorsClassifyConstantsThroughPublicApis() {
        TypeQualifierValidator<Nonnull> nonnullChecker = new Nonnull.Checker();
        TypeQualifierValidator<Nonnegative> nonnegativeChecker = new Nonnegative.Checker();
        TypeQualifierValidator<MatchesPattern> matchesPatternChecker = new MatchesPattern.Checker();
        TypeQualifierValidator<RegEx> regexChecker = new RegEx.Checker();

        assertThat(nonnullChecker.forConstantValue(nonnull(javax.annotation.meta.When.ALWAYS), null))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(nonnullChecker.forConstantValue(nonnull(javax.annotation.meta.When.MAYBE), "metadata-forge"))
                .isSameAs(javax.annotation.meta.When.ALWAYS);

        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), -1))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), 0))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), 42L))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), -2L))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), 0.0d))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), -0.01d))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), 3.5f))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), -3.5f))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(javax.annotation.meta.When.ALWAYS), "not-a-number"))
                .isSameAs(javax.annotation.meta.When.NEVER);

        MatchesPattern mixedCaseIdentifier = matchesPattern("[a-z]{3}\\d{2}", Pattern.CASE_INSENSITIVE);
        assertThat(matchesPatternChecker.forConstantValue(mixedCaseIdentifier, "AbC12"))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(matchesPatternChecker.forConstantValue(mixedCaseIdentifier, "abc"))
                .isSameAs(javax.annotation.meta.When.NEVER);

        RegEx regex = regEx(javax.annotation.meta.When.ALWAYS);
        assertThat(regexChecker.forConstantValue(regex, "[a-z]+(\\d{2})?"))
                .isSameAs(javax.annotation.meta.When.ALWAYS);
        assertThat(regexChecker.forConstantValue(regex, "[unterminated"))
                .isSameAs(javax.annotation.meta.When.NEVER);
        assertThat(regexChecker.forConstantValue(regex, 17))
                .isSameAs(javax.annotation.meta.When.NEVER);
    }

    @Test
    void annotationDrivenFixturesRemainUsableInRegularCodePaths() {
        ManagedResource parent = new ManagedResource("  Parent Resource  ", null);
        ManagedResource child = parent.openChild("  Child Resource  ");

        assertThat(parent.canonicalName()).isEqualTo("parent resource");
        assertThat(parent.aliasOrNull(true)).isNull();
        assertThat(parent.displayName("  PRIMARY OWNER  ")).isEqualTo("primary owner");
        assertThat(parent.displayName(null)).isEqualTo("parent resource");
        assertThat(child.canonicalName()).isEqualTo("parent resource/child resource");
        assertThat(parent.openedChildren()).isEqualTo(1);
        assertThat(parent.close("  done  ")).isEqualTo("parent resource:done");
        assertThat(parent.isClosed()).isTrue();

        GuardedCounter counter = new GuardedCounter();
        assertThat(counter.incrementAndGet()).isEqualTo(1);
        assertThat(counter.incrementAndGet()).isEqualTo(2);
        assertThat(counter.current()).isEqualTo(2);

        ImmutableCoordinate coordinate = new ImmutableCoordinate(" com.google.code.findbugs ", " annotations ");
        assertThat(coordinate.coordinate()).isEqualTo("com.google.code.findbugs:annotations");

        MutableNote mutableNote = new MutableNote("native");
        mutableNote.append(" image");
        mutableNote.append(" tests");
        assertThat(mutableNote.value()).isEqualTo("native image tests");

        LifecycleOverride lifecycle = new LifecycleOverride("  library  ");
        assertThat(lifecycle.finish("  verified  ")).isEqualTo("library:verified:closed");
        assertThat(lifecycle.isClosed()).isTrue();
    }

    private static CleanupObligation cleanupObligation() {
        return new CleanupObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CleanupObligation.class;
            }
        };
    }

    private static CreatesObligation createsObligation() {
        return new CreatesObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CreatesObligation.class;
            }
        };
    }

    private static DischargesObligation dischargesObligation() {
        return new DischargesObligation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return DischargesObligation.class;
            }
        };
    }

    private static CheckForNull checkForNull() {
        return new CheckForNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckForNull.class;
            }
        };
    }

    private static Nullable nullable() {
        return new Nullable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
    }

    private static ParametersAreNonnullByDefault parametersAreNonnullByDefault() {
        return new ParametersAreNonnullByDefault() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ParametersAreNonnullByDefault.class;
            }
        };
    }

    private static ParametersAreNullableByDefault parametersAreNullableByDefault() {
        return new ParametersAreNullableByDefault() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ParametersAreNullableByDefault.class;
            }
        };
    }

    private static OverridingMethodsMustInvokeSuper overridingMethodsMustInvokeSuper() {
        return new OverridingMethodsMustInvokeSuper() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return OverridingMethodsMustInvokeSuper.class;
            }
        };
    }

    private static CheckReturnValue checkReturnValue(javax.annotation.meta.When when) {
        return new CheckReturnValue() {
            @Override
            public javax.annotation.meta.When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckReturnValue.class;
            }
        };
    }

    private static Nonnull nonnull(javax.annotation.meta.When when) {
        return new Nonnull() {
            @Override
            public javax.annotation.meta.When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nonnull.class;
            }
        };
    }

    private static Nonnegative nonnegative(javax.annotation.meta.When when) {
        return new Nonnegative() {
            @Override
            public javax.annotation.meta.When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nonnegative.class;
            }
        };
    }

    @SafeVarargs
    private static Class<? extends Annotation>[] annotationTypes(Class<? extends Annotation>... value) {
        return value;
    }

    private static DefaultAnnotation defaultAnnotation(
            Priority priority,
            Confidence confidence,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotation() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotation.class;
            }
        };
    }

    private static DefaultAnnotationForFields defaultAnnotationForFields(
            Priority priority,
            Confidence confidence,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForFields() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForFields.class;
            }
        };
    }

    private static DefaultAnnotationForMethods defaultAnnotationForMethods(
            Priority priority,
            Confidence confidence,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForMethods() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForMethods.class;
            }
        };
    }

    private static DefaultAnnotationForParameters defaultAnnotationForParameters(
            Priority priority,
            Confidence confidence,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForParameters() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForParameters.class;
            }
        };
    }

    private static DesireWarning desireWarning(String value, Confidence confidence, int rank, int num) {
        return new DesireWarning() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public int rank() {
                return rank;
            }

            @Override
            public int num() {
                return num;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DesireWarning.class;
            }
        };
    }

    private static ExpectWarning expectWarning(String value, Confidence confidence, int rank, int num) {
        return new ExpectWarning() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public int rank() {
                return rank;
            }

            @Override
            public int num() {
                return num;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ExpectWarning.class;
            }
        };
    }

    private static NoWarning noWarning(String value, Confidence confidence, int rank, int num) {
        return new NoWarning() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public int rank() {
                return rank;
            }

            @Override
            public int num() {
                return num;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return NoWarning.class;
            }
        };
    }

    private static SuppressFBWarnings suppressFbWarnings(String justification, String... value) {
        return new SuppressFBWarnings() {
            @Override
            public String[] value() {
                return value;
            }

            @Override
            public String justification() {
                return justification;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return SuppressFBWarnings.class;
            }
        };
    }

    private static MatchesPattern matchesPattern(String value, int flags) {
        return new MatchesPattern() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public int flags() {
                return flags;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return MatchesPattern.class;
            }
        };
    }

    private static RegEx regEx(javax.annotation.meta.When when) {
        return new RegEx() {
            @Override
            public javax.annotation.meta.When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RegEx.class;
            }
        };
    }

    private static net.jcip.annotations.GuardedBy jcipGuardedBy(String value) {
        return new net.jcip.annotations.GuardedBy() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return net.jcip.annotations.GuardedBy.class;
            }
        };
    }

    private static javax.annotation.concurrent.GuardedBy concurrentGuardedBy(String value) {
        return new javax.annotation.concurrent.GuardedBy() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return javax.annotation.concurrent.GuardedBy.class;
            }
        };
    }

    @CleanupObligation
    @ParametersAreNonnullByDefault
    @net.jcip.annotations.ThreadSafe
    @javax.annotation.concurrent.ThreadSafe
    private static final class ManagedResource {
        private final Object stateLock = new Object();

        @Nonnull(when = javax.annotation.meta.When.ALWAYS)
        private final String name;

        @CheckForNull
        private final String alias;

        @net.jcip.annotations.GuardedBy("stateLock")
        @javax.annotation.concurrent.GuardedBy("stateLock")
        private int openedChildren;

        private boolean closed;

        @CreatesObligation
        private ManagedResource(String name, @Nullable String alias) {
            this.name = normalize(name);
            this.alias = normalizeNullable(alias);
        }

        @CheckReturnValue(when = javax.annotation.meta.When.ALWAYS)
        @CreatesObligation
        private ManagedResource openChild(String childName) {
            synchronized (stateLock) {
                openedChildren++;
            }
            return new ManagedResource(name + "/" + normalize(childName), alias);
        }

        @Nonnull(when = javax.annotation.meta.When.ALWAYS)
        private String canonicalName() {
            return name;
        }

        @CheckForNull
        private String aliasOrNull(boolean includeAlias) {
            return includeAlias ? alias : null;
        }

        @Nonnull(when = javax.annotation.meta.When.ALWAYS)
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "The null fallback is exercised directly")
        private String displayName(@Nullable String preferredLabel) {
            String normalizedLabel = normalizeNullable(preferredLabel);
            if (normalizedLabel != null) {
                return normalizedLabel;
            }
            if (alias != null) {
                return alias;
            }
            return name;
        }

        @DischargesObligation
        private String close(String reason) {
            closed = true;
            return name + ":" + normalize(reason);
        }

        private int openedChildren() {
            synchronized (stateLock) {
                return openedChildren;
            }
        }

        private boolean isClosed() {
            return closed;
        }
    }

    @net.jcip.annotations.ThreadSafe
    @javax.annotation.concurrent.ThreadSafe
    private static final class GuardedCounter {
        private final Object lock = new Object();

        @net.jcip.annotations.GuardedBy("lock")
        @javax.annotation.concurrent.GuardedBy("lock")
        private int value;

        private int incrementAndGet() {
            synchronized (lock) {
                value++;
                return value;
            }
        }

        private int current() {
            synchronized (lock) {
                return value;
            }
        }
    }

    @net.jcip.annotations.Immutable
    @javax.annotation.concurrent.Immutable
    private static final class ImmutableCoordinate {
        private final String groupId;
        private final String artifactId;

        private ImmutableCoordinate(String groupId, String artifactId) {
            this.groupId = normalize(groupId);
            this.artifactId = normalize(artifactId);
        }

        private String coordinate() {
            return groupId + ":" + artifactId;
        }
    }

    @ParametersAreNullableByDefault
    @net.jcip.annotations.NotThreadSafe
    @javax.annotation.concurrent.NotThreadSafe
    private static final class MutableNote {
        private final StringBuilder value;

        private MutableNote(String initialValue) {
            this.value = new StringBuilder(normalize(initialValue));
        }

        private void append(String fragment) {
            value.append(' ').append(normalize(fragment));
        }

        private String value() {
            return value.toString();
        }
    }

    @CleanupObligation
    private static class LifecycleBase {
        private final String name;
        private boolean closed;

        @CreatesObligation
        private LifecycleBase(String name) {
            this.name = normalize(name);
        }

        @OverridingMethodsMustInvokeSuper
        @DischargesObligation
        String finish(String reason) {
            closed = true;
            return name + ":" + normalize(reason) + ":closed";
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static final class LifecycleOverride extends LifecycleBase {
        private LifecycleOverride(String name) {
            super(name);
        }

        @Override
        @DischargesObligation
        String finish(String reason) {
            return super.finish(reason);
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
