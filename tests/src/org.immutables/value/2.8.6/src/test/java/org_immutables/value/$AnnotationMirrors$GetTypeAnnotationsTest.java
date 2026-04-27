/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import org.immutables.value.internal.$generator$.$AnnotationMirrors;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class $AnnotationMirrors$GetTypeAnnotationsTest {

    @Test
    void fromUsesTypeMirrorGetAnnotationMirrorsOnImplementations() {
        SimpleAnnotationMirror annotationMirror = new SimpleAnnotationMirror();
        TrackingTypeMirror typeMirror = new TrackingTypeMirror(List.of(annotationMirror));

        List<? extends AnnotationMirror> annotationMirrors = $AnnotationMirrors.from(typeMirror);

        assertThat(annotationMirrors)
                .hasSize(1)
                .isSameAs(typeMirror.annotationMirrors());
        assertThat(annotationMirrors.get(0)).isSameAs(annotationMirror);
        assertThat(typeMirror.getAnnotationMirrorsCalls()).isEqualTo(1);
    }

    private static final class TrackingTypeMirror implements TypeMirror {
        private final List<? extends AnnotationMirror> annotationMirrors;
        private int getAnnotationMirrorsCalls;

        private TrackingTypeMirror(List<? extends AnnotationMirror> annotationMirrors) {
            this.annotationMirrors = annotationMirrors;
        }

        private List<? extends AnnotationMirror> annotationMirrors() {
            return annotationMirrors;
        }

        private int getAnnotationMirrorsCalls() {
            return getAnnotationMirrorsCalls;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            getAnnotationMirrorsCalls++;
            return annotationMirrors;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SimpleAnnotationMirror implements AnnotationMirror {

        @Override
        public DeclaredType getAnnotationType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
            return Map.of();
        }

        @Override
        public String toString() {
            return "@Covered";
        }
    }
}
