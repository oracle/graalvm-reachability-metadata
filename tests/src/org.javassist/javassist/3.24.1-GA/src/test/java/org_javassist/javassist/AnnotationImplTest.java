/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import org.junit.jupiter.api.Test;

public class AnnotationImplTest {
    @Test
    void createsAnnotationProxyThatBehavesLikeRuntimeAnnotation() throws Exception {
        ConstPool constPool = new ConstPool(AnnotationImplTest.class.getName());
        javassist.bytecode.annotation.Annotation annotationMetadata = new javassist.bytecode.annotation.Annotation(
                FixtureAnnotation.class.getName(),
                constPool);
        annotationMetadata.addMemberValue("name", new StringMemberValue("javassist", constPool));
        annotationMetadata.addMemberValue("priority", new IntegerMemberValue(constPool, 24));

        ClassLoader classLoader = AnnotationImplTest.class.getClassLoader();
        FixtureAnnotation annotation = (FixtureAnnotation) annotationMetadata.toAnnotationType(classLoader, null);
        FixtureAnnotation equivalentAnnotation = new FixtureAnnotationImpl("javassist", 24);

        assertThat(annotation.annotationType()).isSameAs(FixtureAnnotation.class);
        assertThat(annotation.name()).isEqualTo("javassist");
        assertThat(annotation.priority()).isEqualTo(24);
        assertThat(annotation.hashCode()).isEqualTo(equivalentAnnotation.hashCode());
        assertThat(annotation).isEqualTo(equivalentAnnotation);
        assertThat(annotation).isNotEqualTo(new FixtureAnnotationImpl("javassist", 25));
    }

    public @interface FixtureAnnotation {
        String name();

        int priority();
    }

    private static final class FixtureAnnotationImpl implements FixtureAnnotation {
        private final String name;
        private final int priority;

        FixtureAnnotationImpl(String name, int priority) {
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
        public Class<? extends Annotation> annotationType() {
            return FixtureAnnotation.class;
        }

        @Override
        public int hashCode() {
            int nameHashCode = (127 * "name".hashCode()) ^ name.hashCode();
            int priorityHashCode = (127 * "priority".hashCode()) ^ Integer.valueOf(priority).hashCode();
            return nameHashCode + priorityHashCode;
        }
    }
}
