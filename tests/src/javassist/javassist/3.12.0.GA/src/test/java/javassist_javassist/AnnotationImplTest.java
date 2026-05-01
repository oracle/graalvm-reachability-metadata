/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationImplTest {
    @Test
    void toAnnotationTypeCreatesProxyThatImplementsAnnotationContract() throws Exception {
        Annotation annotation = createAnnotation("sample", 17);
        FixtureAnnotation proxy = (FixtureAnnotation) annotation.toAnnotationType(
                AnnotationImplTest.class.getClassLoader(), null);
        FixtureAnnotation expectedAnnotation = new FixtureAnnotationLiteral("sample", 17);

        assertThat(proxy.annotationType()).isSameAs(FixtureAnnotation.class);
        assertThat(proxy.value()).isEqualTo("sample");
        assertThat(proxy.count()).isEqualTo(17);
        assertThat(proxy.toString()).contains(FixtureAnnotation.class.getName(), "value=\"sample\"", "count=17");
        assertThat(proxy.hashCode()).isEqualTo(expectedAnnotation.hashCode());
        assertThat(proxy).isEqualTo(expectedAnnotation);
    }

    private static Annotation createAnnotation(String value, int count) {
        ConstPool constPool = new ConstPool(AnnotationImplTest.class.getName());
        Annotation annotation = new Annotation(FixtureAnnotation.class.getName(), constPool);
        annotation.addMemberValue("value", new StringMemberValue(value, constPool));
        annotation.addMemberValue("count", new IntegerMemberValue(constPool, count));
        return annotation;
    }

    public @interface FixtureAnnotation {
        String value();

        int count();
    }

    private static class FixtureAnnotationLiteral implements FixtureAnnotation {
        private final String value;
        private final int count;

        private FixtureAnnotationLiteral(String value, int count) {
            this.value = value;
            this.count = count;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return FixtureAnnotation.class;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FixtureAnnotation)) {
                return false;
            }

            FixtureAnnotation annotation = (FixtureAnnotation) other;
            return value.equals(annotation.value()) && count == annotation.count();
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode() ^ value.hashCode())
                    + (127 * "count".hashCode() ^ Integer.valueOf(count).hashCode());
        }
    }
}
