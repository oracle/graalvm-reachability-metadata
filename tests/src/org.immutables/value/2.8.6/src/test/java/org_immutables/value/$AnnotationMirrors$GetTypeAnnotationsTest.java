/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import org.immutables.value.internal.$generator$.$AnnotationMirrors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationMirrorsGetTypeAnnotationsTest {

    @Test
    void fromUsesTypeMirrorGetAnnotationMirrorsWhenAvailable() {
        AnnotationMirror annotationMirror = new SimpleAnnotationMirror();
        List<AnnotationMirror> annotationMirrors = List.of(annotationMirror);
        RecordingTypeMirror typeMirror = new RecordingTypeMirror(annotationMirrors);
        List<? extends AnnotationMirror> returnedMirrors = $AnnotationMirrors.from(typeMirror);

        assertThat(returnedMirrors).hasSize(1);
        assertThat(returnedMirrors.get(0)).isSameAs(annotationMirror);
        assertThat(typeMirror.invocationCount()).isEqualTo(1);
    }

    private static final class RecordingTypeMirror implements TypeMirror {
        private final List<? extends AnnotationMirror> annotationMirrors;
        private int invocationCount;

        private RecordingTypeMirror(List<? extends AnnotationMirror> annotationMirrors) {
            this.annotationMirrors = annotationMirrors;
        }

        int invocationCount() {
            return invocationCount;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            return visitor.visitUnknown(this, parameter);
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            invocationCount++;
            return annotationMirrors;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return (A[]) new Annotation[0];
        }
    }

    private static final class SimpleAnnotationMirror implements AnnotationMirror {

        @Override
        public DeclaredType getAnnotationType() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
            return Map.of();
        }
    }
}
