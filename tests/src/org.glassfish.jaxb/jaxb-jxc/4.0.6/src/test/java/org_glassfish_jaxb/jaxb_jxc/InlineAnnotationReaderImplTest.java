/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.tools.jxc.ap.InlineAnnotationReaderImpl;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import org.junit.jupiter.api.Test;

public class InlineAnnotationReaderImplTest {
    @Test
    void getClassValueReadsMirroredTypeFromAnnotationMethod() {
        TypeMirror adapterType = new SimpleTypeMirror("example.Adapter");
        SingleClassValueAnnotation annotation = new MirroredSingleClassValueAnnotation(adapterType);

        TypeMirror value = InlineAnnotationReaderImpl.theInstance.getClassValue(annotation, "value");

        assertThat(value).isSameAs(adapterType);
    }

    @Test
    void getClassArrayValueReadsMirroredTypesFromAnnotationMethod() {
        TypeMirror firstType = new SimpleTypeMirror("example.FirstType");
        TypeMirror secondType = new SimpleTypeMirror("example.SecondType");
        ClassArrayValueAnnotation annotation = new MirroredClassArrayValueAnnotation(List.of(firstType, secondType));

        TypeMirror[] values = InlineAnnotationReaderImpl.theInstance.getClassArrayValue(annotation, "value");

        assertThat(values).containsExactly(firstType, secondType);
    }

    @Test
    void getClassValueCoversSuccessfulReflectiveInvocation() {
        SingleClassValueAnnotation annotation = new ReturningSingleClassValueAnnotation(String.class);

        assertThatThrownBy(() -> InlineAnnotationReaderImpl.theInstance.getClassValue(annotation, "value"))
                .isInstanceOfAny(AssertionError.class, IllegalStateException.class);
    }

    @Test
    void getClassArrayValueCoversSuccessfulReflectiveInvocation() {
        ClassArrayValueAnnotation annotation = new ReturningClassArrayValueAnnotation(String.class, Integer.class);

        assertThatThrownBy(() -> InlineAnnotationReaderImpl.theInstance.getClassArrayValue(annotation, "value"))
                .isInstanceOfAny(AssertionError.class, IllegalStateException.class);
    }

    @Test
    void getAllFieldAnnotationsLoadsAnnotationMirrorType() {
        VariableElement field = new MirroredAnnotationField(Deprecated.class.getName());

        Annotation[] annotations = InlineAnnotationReaderImpl.theInstance.getAllFieldAnnotations(field, null);

        assertThat(annotations).isEmpty();
    }

    public @interface SingleClassValueAnnotation {
        Class<?> value();
    }

    public @interface ClassArrayValueAnnotation {
        Class<?>[] value();
    }

    private static final class MirroredSingleClassValueAnnotation implements SingleClassValueAnnotation {
        private final TypeMirror adapterType;

        private MirroredSingleClassValueAnnotation(TypeMirror adapterType) {
            this.adapterType = adapterType;
        }

        @Override
        public Class<?> value() {
            throw new MirroredTypeException(adapterType);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SingleClassValueAnnotation.class;
        }
    }

    private static final class MirroredClassArrayValueAnnotation implements ClassArrayValueAnnotation {
        private final List<? extends TypeMirror> types;

        private MirroredClassArrayValueAnnotation(List<? extends TypeMirror> types) {
            this.types = List.copyOf(types);
        }

        @Override
        public Class<?>[] value() {
            throw new MirroredTypesException(types);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ClassArrayValueAnnotation.class;
        }
    }

    private static final class ReturningSingleClassValueAnnotation implements SingleClassValueAnnotation {
        private final Class<?> type;

        private ReturningSingleClassValueAnnotation(Class<?> type) {
            this.type = type;
        }

        @Override
        public Class<?> value() {
            return type;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SingleClassValueAnnotation.class;
        }
    }

    private static final class ReturningClassArrayValueAnnotation implements ClassArrayValueAnnotation {
        private final Class<?>[] types;

        private ReturningClassArrayValueAnnotation(Class<?>... types) {
            this.types = types.clone();
        }

        @Override
        public Class<?>[] value() {
            return types.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ClassArrayValueAnnotation.class;
        }
    }

    private static final class MirroredAnnotationField implements VariableElement {
        private final AnnotationMirror annotationMirror;

        private MirroredAnnotationField(String annotationClassName) {
            this.annotationMirror = new SimpleAnnotationMirror(annotationClassName);
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of(annotationMirror);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public TypeMirror asType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getConstantValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.FIELD;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Collections.emptySet();
        }

        @Override
        public Name getSimpleName() {
            return new SimpleName("field");
        }

        @Override
        public Element getEnclosingElement() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SimpleAnnotationMirror implements AnnotationMirror {
        private final DeclaredType annotationType;

        private SimpleAnnotationMirror(String annotationClassName) {
            this.annotationType = new SimpleDeclaredType(new SimpleTypeElement(annotationClassName));
        }

        @Override
        public DeclaredType getAnnotationType() {
            return annotationType;
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
            return Collections.emptyMap();
        }
    }

    private static final class SimpleDeclaredType implements DeclaredType {
        private final Element element;

        private SimpleDeclaredType(Element element) {
            this.element = element;
        }

        @Override
        public Element asElement() {
            return element;
        }

        @Override
        public TypeMirror getEnclosingType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return Collections.emptyList();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SimpleTypeElement implements TypeElement {
        private final Name qualifiedName;

        private SimpleTypeElement(String qualifiedName) {
            this.qualifiedName = new SimpleName(qualifiedName);
        }

        @Override
        public Name getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public TypeMirror asType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.ANNOTATION_TYPE;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Collections.emptySet();
        }

        @Override
        public Name getSimpleName() {
            return qualifiedName;
        }

        @Override
        public Element getEnclosingElement() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public TypeMirror getSuperclass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return Collections.emptyList();
        }
    }

    private static final class SimpleTypeMirror implements TypeMirror {
        private final String displayName;

        private SimpleTypeMirror(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class SimpleName implements Name {
        private final String value;

        private SimpleName(String value) {
            this.value = value;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return value.contentEquals(cs);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
