/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_findbugs.findbugs_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.Confidence;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DesireNoWarning;
import edu.umd.cs.findbugs.annotations.DesireWarning;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import edu.umd.cs.findbugs.annotations.ExpectWarning;
import edu.umd.cs.findbugs.annotations.NoWarning;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Findbugs_annotationsTest {
    @Test
    void enumUtilitiesExposeOrderingAndBoundaryMappings() {
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
        assertThat(Confidence.getConfidence(0)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(1)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(2)).isSameAs(Confidence.MEDIUM);
        assertThat(Confidence.getConfidence(3)).isSameAs(Confidence.LOW);
        assertThat(Confidence.getConfidence(4)).isSameAs(Confidence.IGNORE);
        assertThat(Confidence.getConfidence(100)).isSameAs(Confidence.IGNORE);
    }

    @Test
    void enumUtilitiesIncludeLowerOverflowAndHighestInclusiveBoundaryMappings() {
        assertThat(Confidence.getConfidence(Integer.MIN_VALUE)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(-1)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(5)).isSameAs(Confidence.IGNORE);
    }

    @Test
    void classRetainedWarningAnnotationsExposeMembersAndDefaults() throws NoSuchMethodException {
        DesireWarning desireWarning = new DesireWarning() {
            @Override
            public String value() {
                return "NP";
            }

            @Override
            public Confidence confidence() {
                return Confidence.MEDIUM;
            }

            @Override
            public int rank() {
                return 7;
            }

            @Override
            public int num() {
                return 2;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DesireWarning.class;
            }
        };
        ExpectWarning expectWarning = new ExpectWarning() {
            @Override
            public String value() {
                return "RCN";
            }

            @Override
            public Confidence confidence() {
                return Confidence.LOW;
            }

            @Override
            public int rank() {
                return 9;
            }

            @Override
            public int num() {
                return 1;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ExpectWarning.class;
            }
        };
        NoWarning noWarning = new NoWarning() {
            @Override
            public String value() {
                return "DLS";
            }

            @Override
            public Confidence confidence() {
                return Confidence.HIGH;
            }

            @Override
            public int rank() {
                return 3;
            }

            @Override
            public int num() {
                return 0;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return NoWarning.class;
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

        assertThat(desireWarning.value()).isEqualTo("NP");
        assertThat(desireWarning.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(desireWarning.rank()).isEqualTo(7);
        assertThat(desireWarning.num()).isEqualTo(2);
        assertThat(desireWarning.annotationType()).isSameAs(DesireWarning.class);

        assertThat(expectWarning.value()).isEqualTo("RCN");
        assertThat(expectWarning.confidence()).isEqualTo(Confidence.LOW);
        assertThat(expectWarning.rank()).isEqualTo(9);
        assertThat(expectWarning.num()).isEqualTo(1);
        assertThat(expectWarning.annotationType()).isSameAs(ExpectWarning.class);

        assertThat(noWarning.value()).isEqualTo("DLS");
        assertThat(noWarning.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(noWarning.rank()).isEqualTo(3);
        assertThat(noWarning.num()).isZero();
        assertThat(noWarning.annotationType()).isSameAs(NoWarning.class);

        assertThat(suppressFBWarnings.value()).containsExactly("NP_NULL_ON_SOME_PATH", "UWF_UNWRITTEN_FIELD");
        assertThat(suppressFBWarnings.justification()).isEqualTo("Verified by the test fixture");
        assertThat(suppressFBWarnings.annotationType()).isSameAs(SuppressFBWarnings.class);

        assertThat(DesireWarning.class.getAnnotationsByType(Retention.class)).hasSize(1);
        assertThat(DesireWarning.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(ExpectWarning.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(NoWarning.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(SuppressFBWarnings.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(DesireWarning.class.getAnnotationsByType(Target.class)).isEmpty();
        assertThat(ExpectWarning.class.getAnnotationsByType(Target.class)).isEmpty();
        assertThat(NoWarning.class.getAnnotationsByType(Target.class)).isEmpty();
        assertThat(SuppressFBWarnings.class.getAnnotationsByType(Target.class)).isEmpty();

        assertThat(DesireWarning.class.getDeclaredMethod("confidence").getDefaultValue()).isEqualTo(Confidence.LOW);
        assertThat(DesireWarning.class.getDeclaredMethod("rank").getDefaultValue()).isEqualTo(20);
        assertThat(DesireWarning.class.getDeclaredMethod("num").getDefaultValue()).isEqualTo(1);
        assertThat(ExpectWarning.class.getDeclaredMethod("confidence").getDefaultValue()).isEqualTo(Confidence.LOW);
        assertThat(ExpectWarning.class.getDeclaredMethod("rank").getDefaultValue()).isEqualTo(20);
        assertThat(ExpectWarning.class.getDeclaredMethod("num").getDefaultValue()).isEqualTo(1);
        assertThat(NoWarning.class.getDeclaredMethod("confidence").getDefaultValue()).isEqualTo(Confidence.LOW);
        assertThat(NoWarning.class.getDeclaredMethod("rank").getDefaultValue()).isEqualTo(20);
        assertThat(NoWarning.class.getDeclaredMethod("num").getDefaultValue()).isEqualTo(0);
        assertThat((String[]) SuppressFBWarnings.class.getDeclaredMethod("value").getDefaultValue()).isEmpty();
        assertThat(SuppressFBWarnings.class.getDeclaredMethod("justification").getDefaultValue()).isEqualTo("");
    }

    @Test
    void classRetainedWarningAnnotationsDoNotLeakIntoRuntimeReflection()
            throws NoSuchFieldException, NoSuchMethodException {
        assertThat(WarningFixture.class.getDeclaredAnnotationsByType(SuppressFBWarnings.class)).isEmpty();
        assertThat(WarningFixture.class.getDeclaredField("statusCode").getDeclaredAnnotationsByType(NoWarning.class)).isEmpty();
        assertThat(WarningFixture.class.getDeclaredConstructor(String.class).getDeclaredAnnotationsByType(ExpectWarning.class)).isEmpty();
        assertThat(WarningFixture.class.getDeclaredMethod("normalize", String.class).getDeclaredAnnotationsByType(NoWarning.class))
                .isEmpty();
        assertThat(WarningFixture.class
                .getDeclaredMethod("normalize", String.class)
                .getParameters()[0]
                .getDeclaredAnnotationsByType(SuppressFBWarnings.class)).isEmpty();
    }

    @Test
    void desireNoWarningAnnotatedCodeRemainsUsableForWarningFreePaths() {
        DesireNoWarningFixture fixture = new DesireNoWarningFixture("  healthy  ");

        assertThat(fixture.canonicalName()).isEqualTo("healthy");
        assertThat(fixture.createStableCopy()).isEqualTo("healthy");
        assertThat(fixture.combine("  PATH  ")).isEqualTo("healthy:path");
    }

    @Test
    void runtimeObligationAnnotationsRemainVisibleAndSupportNormalUsage() throws NoSuchMethodException {
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
        assertThat(DischargesObligation.class.getAnnotationsByType(Target.class)[0].value()).containsExactly(ElementType.METHOD);
    }

    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "Covered by reflection tests")
    private static final class WarningFixture {
        @NoWarning("UUF_UNUSED_FIELD")
        private final int statusCode = 200;

        @ExpectWarning("NP")
        private WarningFixture(@DesireWarning("NP") String label) {
            if (label.isBlank()) {
                throw new IllegalArgumentException("label must not be blank");
            }
        }

        @NoWarning("DLS")
        private String normalize(@SuppressFBWarnings("DM_DEFAULT_ENCODING") String value) {
            @ExpectWarning("DLS")
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return normalized;
        }
    }

    @DesireNoWarning("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private static final class DesireNoWarningFixture {
        @DesireNoWarning("UUF_UNUSED_FIELD")
        private final String normalizedName;

        @DesireNoWarning("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
        private DesireNoWarningFixture(String rawName) {
            normalizedName = sanitize(rawName);
        }

        @DesireNoWarning("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
        private String canonicalName() {
            return normalizedName;
        }

        @DesireNoWarning("DLS_DEAD_LOCAL_STORE")
        private String createStableCopy() {
            @DesireNoWarning("DLS_DEAD_LOCAL_STORE")
            String stableCopy = normalizedName;
            return stableCopy;
        }

        private String combine(@DesireNoWarning("NP") String rawSuffix) {
            return normalizedName + ":" + sanitize(rawSuffix);
        }

        private static String sanitize(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
            return normalized;
        }
    }

    @CleanupObligation
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
