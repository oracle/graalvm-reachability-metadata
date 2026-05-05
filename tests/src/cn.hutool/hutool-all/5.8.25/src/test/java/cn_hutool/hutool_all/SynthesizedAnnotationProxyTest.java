/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.annotation.SynthesizedAggregateAnnotation;
import cn.hutool.core.annotation.SynthesizedAnnotation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class SynthesizedAnnotationProxyTest {

    @Test
    void synthesizeCreatesProxyAnnotationFromAggregateAnnotation() {
        Class<AnnotatedType> annotatedTypeAnnotationAccess = AnnotatedType.class;
        Route sourceAnnotation = annotatedTypeAnnotationAccess.getAnnotation(Route.class);
        assertThat(sourceAnnotation).isNotNull();
        SynthesizedAggregateAnnotation aggregateAnnotation = AnnotationUtil.aggregatingFromAnnotation(sourceAnnotation);

        Route synthesizedAnnotation = aggregateAnnotation.synthesize(Route.class);

        assertThat(synthesizedAnnotation).isNotNull();
        assertThat(synthesizedAnnotation).isNotSameAs(sourceAnnotation);
        assertThat(AnnotationUtil.isSynthesizedAnnotation(synthesizedAnnotation)).isTrue();
        assertThat(synthesizedAnnotation).isInstanceOf(SynthesizedAnnotation.class);
        assertThat(synthesizedAnnotation.annotationType()).isEqualTo(Route.class);
        assertThat(synthesizedAnnotation.path()).isEqualTo("/orders");
        assertThat(synthesizedAnnotation.method()).isEqualTo("POST");
    }

    @Route(path = "/orders", method = "POST")
    private static class AnnotatedType {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Route {
        String path();

        String method() default "GET";
    }
}
