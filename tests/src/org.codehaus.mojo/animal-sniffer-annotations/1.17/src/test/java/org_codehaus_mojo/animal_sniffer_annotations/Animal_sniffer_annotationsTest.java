/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_mojo.animal_sniffer_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.assertj.core.api.Assertions;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.Test;

class Animal_sniffer_annotationsTest {
    @Test
    void ignoreJreRequirementIsAMarkerAnnotationType() {
        IgnoreJRERequirement ignoreJRERequirement = new IgnoreJRERequirement() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return IgnoreJRERequirement.class;
            }
        };

        Assertions.assertThat(IgnoreJRERequirement.class.isAnnotation()).isTrue();
        Assertions.assertThat(Annotation.class.isAssignableFrom(IgnoreJRERequirement.class)).isTrue();
        Assertions.assertThat(ignoreJRERequirement.annotationType()).isSameAs(IgnoreJRERequirement.class);
        Assertions.assertThat(IgnoreJRERequirement.class.getDeclaredMethods()).isEmpty();
    }

    @Test
    void ignoreJreRequirementDeclaresItsSupportedTargetsAndRetention() {
        Target target = IgnoreJRERequirement.class.getAnnotation(Target.class);
        Retention retention = IgnoreJRERequirement.class.getAnnotation(Retention.class);

        Assertions.assertThat(IgnoreJRERequirement.class.isAnnotationPresent(Documented.class)).isTrue();
        Assertions.assertThat(target).isNotNull();
        Assertions.assertThat(target.value())
                .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD);
        Assertions.assertThat(retention).isNotNull();
        Assertions.assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void ignoreJreRequirementAnnotatedCodeExecutesNormally() {
        AnnotatedCompatibilityLayer compatibilityLayer = new AnnotatedCompatibilityLayer("nio");

        Assertions.assertThat(compatibilityLayer.describeFeature("paths")).isEqualTo("nio-paths-ready");
        Assertions.assertThat(compatibilityLayer.describeFeature("channels")).isEqualTo("nio-channels-ready");
    }

    @Test
    void classRetentionKeepsIgnoreJreRequirementInvisibleAtRuntime() throws NoSuchMethodException {
        Class<AnnotatedCompatibilityLayer> compatibilityLayerClass = AnnotatedCompatibilityLayer.class;

        Assertions.assertThat(compatibilityLayerClass.getDeclaredAnnotations()).isEmpty();
        Assertions.assertThat(compatibilityLayerClass.getDeclaredConstructor(String.class).getDeclaredAnnotations())
                .isEmpty();
        Assertions.assertThat(compatibilityLayerClass.getDeclaredMethod("describeFeature", String.class)
                .getDeclaredAnnotations()).isEmpty();
    }

    @IgnoreJRERequirement
    private static final class AnnotatedCompatibilityLayer {
        private final String profile;

        @IgnoreJRERequirement
        private AnnotatedCompatibilityLayer(String profile) {
            this.profile = profile;
        }

        @IgnoreJRERequirement
        private String describeFeature(String feature) {
            return profile + "-" + feature + "-ready";
        }
    }
}
