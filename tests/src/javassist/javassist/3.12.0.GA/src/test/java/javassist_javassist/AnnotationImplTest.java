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
    void createsAnnotationProxyAndComparesItWithAnnotationImplementation() throws Exception {
        SampleAnnotation proxy = newAnnotationProxy("generated", 42);
        SampleAnnotation equalAnnotation = new SampleAnnotationLiteral("generated", 42);
        SampleAnnotation differentAnnotation = new SampleAnnotationLiteral("different", 42);

        assertThat(proxy.annotationType()).isEqualTo(SampleAnnotation.class);
        assertThat(proxy.name()).isEqualTo("generated");
        assertThat(proxy.priority()).isEqualTo(42);
        assertThat(proxy.hashCode()).isEqualTo(equalAnnotation.hashCode());
        assertThat(proxy.equals(equalAnnotation)).isTrue();
        assertThat(proxy.equals(differentAnnotation)).isFalse();
    }

    private static SampleAnnotation newAnnotationProxy(String name, int priority) throws ClassNotFoundException {
        ConstPool constPool = new ConstPool(AnnotationImplTest.class.getName());
        Annotation annotation = new Annotation(SampleAnnotation.class.getName(), constPool);
        annotation.addMemberValue("name", new StringMemberValue(name, constPool));
        annotation.addMemberValue("priority", new IntegerMemberValue(constPool, priority));

        ClassLoader classLoader = AnnotationImplTest.class.getClassLoader();
        return (SampleAnnotation) annotation.toAnnotationType(classLoader, null);
    }

    public @interface SampleAnnotation {
        String name();

        int priority();
    }

    private static final class SampleAnnotationLiteral implements SampleAnnotation {
        private final String name;
        private final int priority;

        private SampleAnnotationLiteral(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return SampleAnnotation.class;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SampleAnnotation)) {
                return false;
            }
            SampleAnnotation annotation = (SampleAnnotation) other;
            return name.equals(annotation.name()) && priority == annotation.priority();
        }

        @Override
        public int hashCode() {
            int nameHash = (127 * "name".hashCode()) ^ name.hashCode();
            int priorityHash = (127 * "priority".hashCode()) ^ Integer.valueOf(priority).hashCode();
            return nameHash + priorityHash;
        }

        @Override
        public String toString() {
            return "@" + SampleAnnotation.class.getName() + "(name=" + name + ", priority=" + priority + ")";
        }
    }
}
