/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationImpl;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;

public class AnnotationImplTest {
    @Test
    void proxyBackedAnnotationExposesTypeMembersHashCodeAndEqualsJdkAnnotation() {
        SampleAnnotation proxyAnnotation = createAnnotationProxy("configured", 42);
        SampleAnnotation runtimeAnnotation = AnnotatedFixture.class.getAnnotation(SampleAnnotation.class);

        assertThat(proxyAnnotation.annotationType()).isSameAs(SampleAnnotation.class);
        assertThat(proxyAnnotation.value()).isEqualTo("configured");
        assertThat(proxyAnnotation.count()).isEqualTo(42);
        assertThat(proxyAnnotation.toString()).contains(SampleAnnotation.class.getName());
        assertThat(proxyAnnotation.hashCode()).isEqualTo(runtimeAnnotation.hashCode());
        assertThat(proxyAnnotation).isEqualTo(runtimeAnnotation);
    }

    private static SampleAnnotation createAnnotationProxy(String value, int count) {
        ConstPool constPool = new ConstPool(AnnotationImplTest.class.getName());
        Annotation annotation = new Annotation(SampleAnnotation.class.getName(), constPool);
        annotation.addMemberValue("value", new StringMemberValue(value, constPool));
        annotation.addMemberValue("count", new IntegerMemberValue(constPool, count));

        ClassLoader classLoader = AnnotationImplTest.class.getClassLoader();
        Object proxy = AnnotationImpl.make(classLoader, SampleAnnotation.class, null, annotation);

        return SampleAnnotation.class.cast(proxy);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {
        String value();

        int count();
    }

    @SampleAnnotation(value = "configured", count = 42)
    private static final class AnnotatedFixture {
    }
}
