/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import org.junit.jupiter.api.Test;

public class AnnotationImplTest {
    @Test
    void convertsBytecodeAnnotationToRuntimeProxyAndComparesWithJvmAnnotation()
            throws Exception {
        Annotation annotation = newFixtureAnnotation();

        RuntimeFixtureAnnotation proxy = (RuntimeFixtureAnnotation) annotation.toAnnotationType(
                AnnotationImplTest.class.getClassLoader(), null);
        RuntimeFixtureAnnotation runtimeAnnotation = AnnotatedFixture.class.getAnnotationsByType(
                RuntimeFixtureAnnotation.class)[0];

        assertThat(proxy.annotationType()).isSameAs(RuntimeFixtureAnnotation.class);
        assertThat(proxy.name()).isEqualTo("alpha");
        assertThat(proxy.number()).isEqualTo(7);
        assertThat(proxy.toString()).contains(RuntimeFixtureAnnotation.class.getName());
        assertThat(proxy.hashCode()).isEqualTo(runtimeAnnotation.hashCode());
        assertThat(proxy.equals(runtimeAnnotation)).isTrue();
    }

    private static Annotation newFixtureAnnotation() {
        ConstPool constPool = new ConstPool(AnnotationImplTest.class.getName());
        Annotation annotation = new Annotation(RuntimeFixtureAnnotation.class.getName(), constPool);
        annotation.addMemberValue("name", new StringMemberValue("alpha", constPool));
        annotation.addMemberValue("number", new IntegerMemberValue(constPool, 7));
        return annotation;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface RuntimeFixtureAnnotation {
        String name();

        int number();
    }

    @RuntimeFixtureAnnotation(name = "alpha", number = 7)
    private static final class AnnotatedFixture {
    }
}
