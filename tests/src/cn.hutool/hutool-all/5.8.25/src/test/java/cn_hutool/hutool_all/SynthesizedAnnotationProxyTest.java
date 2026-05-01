/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.annotation.SynthesizedAnnotation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

public class SynthesizedAnnotationProxyTest {
    @Test
    public void createsDynamicProxyForSynthesizedMetaAnnotation() {
        MetaMarker synthesized = AnnotationUtil.getSynthesizedAnnotation(
                AnnotatedSubject.class, MetaMarker.class);

        assertThat(synthesized).isNotNull();
        assertThat(AnnotationUtil.isSynthesizedAnnotation(synthesized)).isTrue();
        assertThat(synthesized.annotationType()).isEqualTo(MetaMarker.class);
        assertThat(synthesized.value()).isEqualTo("composed-value");
        assertThat(synthesized.priority()).isEqualTo(7);
        assertThat(((SynthesizedAnnotation) synthesized).getVerticalDistance()).isEqualTo(1);
        assertThat(synthesized.toString()).contains("composed-value");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    public @interface MetaMarker {
        String value() default "meta-value";

        int priority() default 1;
    }

    @MetaMarker(value = "meta-value", priority = 1)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ComposedMarker {
        String value() default "composed-default";

        int priority() default 2;
    }

    @ComposedMarker(value = "composed-value", priority = 7)
    public static class AnnotatedSubject {
    }
}
