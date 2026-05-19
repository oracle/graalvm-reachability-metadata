/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;

public class AbstractAspectJAdvisorFactoryTest {

    @Test
    void acceptsAnnotationStyleAspectWithAjcCompiledMarkerField() {
        ReflectiveAspectJAdvisorFactory factory = new ReflectiveAspectJAdvisorFactory();

        boolean aspect = factory.isAspect(AjcCompiledMarkerAspect.class);

        assertThat(aspect).isTrue();
    }

    @Test
    void rejectsClassWithoutAspectAnnotation() {
        ReflectiveAspectJAdvisorFactory factory = new ReflectiveAspectJAdvisorFactory();

        boolean aspect = factory.isAspect(NonAspect.class);

        assertThat(aspect).isFalse();
    }

    @Test
    void acceptsAnnotationStyleAspectWithoutAjcCompiledMarkerField() {
        ReflectiveAspectJAdvisorFactory factory = new ReflectiveAspectJAdvisorFactory();

        boolean aspect = factory.isAspect(AnnotationStyleAspect.class);

        assertThat(aspect).isTrue();
    }

    @Aspect
    public static class AjcCompiledMarkerAspect {
        private Object ajc$perSingletonInstance;
    }

    @Aspect
    public static class AnnotationStyleAspect {
    }

    public static class NonAspect {
    }
}
