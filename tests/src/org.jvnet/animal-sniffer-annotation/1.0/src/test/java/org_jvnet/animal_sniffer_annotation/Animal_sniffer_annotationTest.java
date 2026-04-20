/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jvnet.animal_sniffer_annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.Objects;

import org.jvnet.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Animal_sniffer_annotationTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void ignoreJreRequirementExposesItsAnnotationContract() {
        Class<IgnoreJRERequirement> annotationType = IgnoreJRERequirement.class;
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);
        Documented documented = annotationType.getAnnotation(Documented.class);

        assertThat(annotationType.isAnnotation()).isTrue();
        assertThat(Annotation.class.isAssignableFrom(annotationType)).isTrue();
        assertThat(annotationType.getName()).isEqualTo("org.jvnet.animal_sniffer.IgnoreJRERequirement");
        assertThat(documented).isNotNull();
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
        assertThat(target).isNotNull();
        assertThat(target.value())
                .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
    }

    @Test
    void ignoreJreRequirementRemainsAMarkerAnnotation() {
        Class<IgnoreJRERequirement> annotationType = IgnoreJRERequirement.class;

        assertThat(annotationType.getDeclaredMethods()).isEmpty();
        assertThat(annotationType.getDeclaredFields()).isEmpty();
    }

    @Test
    void ignoreJreRequirementAnnotatedCodePathsExecuteNormally() {
        LegacyApiFacade legacyApiFacade = new LegacyApiFacade("animal-sniffer");

        assertThat(legacyApiFacade.label()).isEqualTo("animal-sniffer");
        assertThat(legacyApiFacade.describeSupport("native image"))
                .isEqualTo("animal-sniffer -> NATIVE IMAGE");
        assertThat(legacyApiFacade.describeSupport("metadata forge"))
                .isEqualTo("animal-sniffer -> METADATA FORGE");
    }

    @Test
    @SuppressWarnings("annotationAccess")
    void classRetainedAnnotationStaysInvisibleAtRuntime() throws NoSuchMethodException {
        Class<LegacyApiFacade> legacyApiFacadeType = LegacyApiFacade.class;

        assertThat(legacyApiFacadeType.getDeclaredAnnotation(IgnoreJRERequirement.class)).isNull();
        assertThat(legacyApiFacadeType.getDeclaredConstructor(String.class)
                .getDeclaredAnnotation(IgnoreJRERequirement.class)).isNull();
        assertThat(legacyApiFacadeType.getDeclaredMethod("describeSupport", String.class)
                .getDeclaredAnnotation(IgnoreJRERequirement.class)).isNull();
    }

    @Test
    void ignoreJreRequirementAllowsCodeToUseNewerJdkApisWithoutChangingBehavior() {
        NewerJreApiFacade newerJreApiFacade = new NewerJreApiFacade();

        assertThat(newerJreApiFacade.describeRequiredApi(null)).isEqualTo("requires: java.nio.file.Path");
        assertThat(newerJreApiFacade.describeRequiredApi("java.util.Optional"))
                .isEqualTo("requires: java.util.Optional");
    }

    @Test
    void ignoreJreRequirementSupportsAnnotatedInterfacesAndDefaultMethods() {
        CompatibilityContract compatibilityContract = new CompatibilityContractImpl("animal-sniffer");

        assertThat(compatibilityContract.describeCompatibility("java.util.Optional"))
                .isEqualTo("animal-sniffer supports java.util.Optional");
        assertThat(compatibilityContract.describeCompatibility("java.time.Instant"))
                .isEqualTo("animal-sniffer supports java.time.Instant");
    }

    private static final class NewerJreApiFacade {
        @IgnoreJRERequirement
        private String describeRequiredApi(String apiName) {
            return "requires: " + Objects.requireNonNullElse(apiName, Path.class.getName());
        }
    }

    @IgnoreJRERequirement
    private interface CompatibilityContract {
        String libraryName();

        @IgnoreJRERequirement
        default String describeCompatibility(String apiName) {
            return libraryName() + " supports " + apiName;
        }
    }

    private static final class CompatibilityContractImpl implements CompatibilityContract {
        private final String libraryName;

        private CompatibilityContractImpl(String libraryName) {
            this.libraryName = libraryName;
        }

        @Override
        public String libraryName() {
            return libraryName;
        }
    }

    @IgnoreJRERequirement
    private static final class LegacyApiFacade {
        private final String label;

        @IgnoreJRERequirement
        private LegacyApiFacade(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        @IgnoreJRERequirement
        private String describeSupport(String capability) {
            return label + " -> " + capability.toUpperCase();
        }
    }
}
