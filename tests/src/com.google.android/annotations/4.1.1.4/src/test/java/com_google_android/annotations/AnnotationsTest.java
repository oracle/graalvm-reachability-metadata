/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_android.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationsTest {
    @Test
    void annotationTypesAndManualImplementationsExposeConfiguredMembers() {
        assertThat(SuppressLint.class).isNotNull();
        assertThat(TargetApi.class).isNotNull();

        SuppressLint suppressLint = new SuppressLint() {
            @Override
            public String[] value() {
                return new String[] {"NewApi", "UnusedAttribute"};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return SuppressLint.class;
            }
        };
        TargetApi targetApi = new TargetApi() {
            @Override
            public int value() {
                return 34;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return TargetApi.class;
            }
        };

        assertThat(suppressLint.value()).containsExactly("NewApi", "UnusedAttribute");
        assertThat(suppressLint.annotationType()).isSameAs(SuppressLint.class);
        assertThat(targetApi.value()).isEqualTo(34);
        assertThat(targetApi.annotationType()).isSameAs(TargetApi.class);
    }

    @Test
    void suppressLintAndTargetApiExposeTheirAnnotationContracts() {
        Target suppressLintTarget = SuppressLint.class.getAnnotation(Target.class);
        Retention suppressLintRetention = SuppressLint.class.getAnnotation(Retention.class);
        Target targetApiTarget = TargetApi.class.getAnnotation(Target.class);
        Retention targetApiRetention = TargetApi.class.getAnnotation(Retention.class);

        assertThat(suppressLintTarget).isNotNull();
        assertThat(suppressLintTarget.value())
                .containsExactly(
                        ElementType.TYPE,
                        ElementType.FIELD,
                        ElementType.METHOD,
                        ElementType.PARAMETER,
                        ElementType.CONSTRUCTOR,
                        ElementType.LOCAL_VARIABLE);
        assertThat(suppressLintRetention).isNotNull();
        assertThat(suppressLintRetention.value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(targetApiTarget).isNotNull();
        assertThat(targetApiTarget.value())
                .containsExactly(ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertThat(targetApiRetention).isNotNull();
        assertThat(targetApiRetention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void suppressLintAndTargetApiAnnotatedCodePathsExecuteNormally() {
        CompatibilityFormatter formatter = new CompatibilityFormatter("material-card");

        assertThat(formatter.render(" ready ")).isEqualTo("material-card:READY");
        assertThat(formatter.renderSteps(List.of(" create ", "bind", " recycle ")))
                .containsExactly("MATERIAL-CARD-CREATE", "MATERIAL-CARD-BIND", "MATERIAL-CARD-RECYCLE");
        assertThat(formatter.history()).containsExactly("READY", "CREATE", "BIND", "RECYCLE");
    }

    @Test
    void targetApiAnnotatedCompatibilityPlanningRemainsUsable() {
        FeaturePlanner planner = new FeaturePlanner(26);

        assertThat(planner.minimumSupportedLevel()).isEqualTo(26);
        assertThat(planner.describe(25)).isEqualTo("legacy-back/shared-storage/unsupported");
        assertThat(planner.describe(29)).isEqualTo("legacy-back/scoped-storage/supported");
        assertThat(planner.describe(33)).isEqualTo("predictive-back/scoped-storage/supported");
    }

    @Test
    void classRetainedAnnotationsStayInvisibleAtRuntime() throws NoSuchFieldException, NoSuchMethodException {
        assertThat(CompatibilityFormatter.class.getDeclaredAnnotations()).isEmpty();
        assertThat(FeaturePlanner.class.getDeclaredAnnotations()).isEmpty();
        assertThat(CompatibilityFormatter.class.getDeclaredField("componentName").getDeclaredAnnotations())
                .isEmpty();
        assertThat(CompatibilityFormatter.class.getDeclaredConstructor(String.class).getDeclaredAnnotations())
                .isEmpty();
        assertThat(CompatibilityFormatter.class.getDeclaredMethod("render", String.class).getDeclaredAnnotations())
                .isEmpty();
        assertThat(
                CompatibilityFormatter.class
                        .getDeclaredMethod("render", String.class)
                        .getParameters()[0]
                        .getDeclaredAnnotations())
                .isEmpty();
    }

    @SuppressLint({"NewApi", "RtlHardcoded"})
    @TargetApi(21)
    private static final class CompatibilityFormatter {
        @SuppressLint("MutableBareField")
        private final String componentName;

        @SuppressLint("UseSparseArrays")
        private final List<String> history = new ArrayList<>();

        @TargetApi(21)
        @SuppressLint("NoClone")
        private CompatibilityFormatter(@SuppressLint("UnknownNullness") String componentName) {
            this.componentName = componentName;
        }

        @TargetApi(24)
        @SuppressLint({"DefaultLocale", "StringFormatInvalid"})
        private String render(@SuppressLint("DefaultLocale") String state) {
            @SuppressLint("LocalSuppress")
            String normalizedState = state.trim().toUpperCase(Locale.ROOT);
            history.add(normalizedState);
            return componentName + ":" + normalizedState;
        }

        @SuppressLint("DefaultLocale")
        private List<String> renderSteps(List<String> steps) {
            List<String> renderedSteps = new ArrayList<>();

            for (String step : steps) {
                @SuppressLint("LocalSuppress")
                String normalizedStep = step.trim().toUpperCase(Locale.ROOT);
                history.add(normalizedStep);
                renderedSteps.add(componentName.toUpperCase(Locale.ROOT) + "-" + normalizedStep);
            }

            return List.copyOf(renderedSteps);
        }

        private List<String> history() {
            return List.copyOf(history);
        }
    }

    @TargetApi(26)
    private static final class FeaturePlanner {
        private final int minimumSupportedLevel;

        @TargetApi(26)
        private FeaturePlanner(int minimumSupportedLevel) {
            this.minimumSupportedLevel = minimumSupportedLevel;
        }

        private int minimumSupportedLevel() {
            return minimumSupportedLevel;
        }

        @TargetApi(33)
        private String backNavigationMode(int currentApiLevel) {
            return currentApiLevel >= 33 ? "predictive-back" : "legacy-back";
        }

        @TargetApi(29)
        private String storageMode(int currentApiLevel) {
            return currentApiLevel >= 29 ? "scoped-storage" : "shared-storage";
        }

        @TargetApi(26)
        private String supportState(int currentApiLevel) {
            return currentApiLevel >= minimumSupportedLevel ? "supported" : "unsupported";
        }

        private String describe(int currentApiLevel) {
            return backNavigationMode(currentApiLevel)
                    + "/"
                    + storageMode(currentApiLevel)
                    + "/"
                    + supportState(currentApiLevel);
        }
    }
}
