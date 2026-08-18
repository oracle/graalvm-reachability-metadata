/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_processor;

import org.graalvm.internal.tck.NativeImageSupport;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.processor.validation.MockSessionFactory;
import org.hibernate.processor.validation.ProcessorSessionFactory;
import org.junit.jupiter.api.Test;

import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class MockSessionFactoryInnerMockJavaTypeTest {
    @Test
    void resolvesManagedDomainJavaTypeClassFromProcessorSessionFactory() {
        try {
            final MockSessionFactory sessionFactory = ProcessorSessionFactory.create(
                    new MinimalProcessingEnvironment(), Map.of(), Map.of(), false);

            final ManagedDomainType<?> managedType = sessionFactory.getJpaMetamodel().findEntityType(Book.class);

            assertThat(managedType).isNotNull();
            assertThat(managedType.getJavaType()).isEqualTo(Book.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class MinimalProcessingEnvironment implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return null;
        }

        @Override
        public Filer getFiler() {
            return null;
        }

        @Override
        public Elements getElementUtils() {
            return new EntityElements();
        }

        @Override
        public Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public boolean isPreviewEnabled() {
            return false;
        }
    }

    private static final class Book {
    }

    private static final class EntityElements implements Elements {
        private final TypeElement entity = new SimpleTypeElement(Book.class.getName(), true);

        @Override
        public PackageElement getPackageElement(CharSequence name) {
            return null;
        }

        @Override
        public TypeElement getTypeElement(CharSequence name) {
            return Book.class.getName().contentEquals(name) ? entity : null;
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
                AnnotationMirror annotation) {
            throw new UnsupportedOperationException("Annotation values are not used by this test");
        }

        @Override
        public String getDocComment(Element element) {
            throw new UnsupportedOperationException("Documentation comments are not used by this test");
        }

        @Override
        public boolean isDeprecated(Element element) {
            return false;
        }

        @Override
        public Name getBinaryName(TypeElement type) {
            throw new UnsupportedOperationException("Binary names are not used by this test");
        }

        @Override
        public PackageElement getPackageOf(Element type) {
            throw new UnsupportedOperationException("Packages are not used by this test");
        }

        @Override
        public List<? extends Element> getAllMembers(TypeElement type) {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
            return List.of();
        }

        @Override
        public boolean hides(Element hider, Element hidden) {
            return false;
        }

        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
            return false;
        }

        @Override
        public String getConstantExpression(Object value) {
            throw new UnsupportedOperationException("Constant expressions are not used by this test");
        }

        @Override
        public void printElements(Writer writer, Element... elements) {
            throw new UnsupportedOperationException("Element printing is not used by this test");
        }

        @Override
        public Name getName(CharSequence characters) {
            throw new UnsupportedOperationException("Names are not used by this test");
        }

        @Override
        public boolean isFunctionalInterface(TypeElement type) {
            return false;
        }
    }

    private static final class SimpleTypeElement implements TypeElement {
        private final String qualifiedName;
        private final List<AnnotationMirror> annotations;

        private SimpleTypeElement(String qualifiedName, boolean entity) {
            this.qualifiedName = qualifiedName;
            this.annotations = entity
                    ? List.of(new SimpleAnnotationMirror("jakarta.persistence.Entity"))
                    : List.of();
        }

        @Override
        public TypeMirror asType() {
            return new SimpleDeclaredType(this);
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.CLASS;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            final int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
            return new SimpleName(qualifiedName.substring(separator + 1));
        }

        @Override
        public Element getEnclosingElement() {
            final int separator = qualifiedName.lastIndexOf('.');
            return separator < 0 ? null : new SimplePackageElement(qualifiedName.substring(0, separator));
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return annotations;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return null;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException("Element visitors are not used by this test");
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            return new SimpleName(qualifiedName);
        }

        @Override
        public TypeMirror getSuperclass() {
            return SimpleNoType.INSTANCE;
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            return List.of();
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return List.of();
        }
    }

    private static final class SimplePackageElement implements PackageElement {
        private final String qualifiedName;

        private SimplePackageElement(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        @Override
        public TypeMirror asType() {
            return SimpleNoType.INSTANCE;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            final int separator = qualifiedName.lastIndexOf('.');
            return new SimpleName(qualifiedName.substring(separator + 1));
        }

        @Override
        public Element getEnclosingElement() {
            return null;
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return null;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException("Element visitors are not used by this test");
        }

        @Override
        public Name getQualifiedName() {
            return new SimpleName(qualifiedName);
        }

        @Override
        public boolean isUnnamed() {
            return qualifiedName.isEmpty();
        }
    }

    private static final class SimpleAnnotationMirror implements AnnotationMirror {
        private final DeclaredType annotationType;

        private SimpleAnnotationMirror(String annotationClassName) {
            this.annotationType = new SimpleDeclaredType(new SimpleTypeElement(annotationClassName, false));
        }

        @Override
        public DeclaredType getAnnotationType() {
            return annotationType;
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
            return Map.of();
        }
    }

    private static final class SimpleDeclaredType implements DeclaredType {
        private final TypeElement element;

        private SimpleDeclaredType(TypeElement element) {
            this.element = element;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return null;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException("Type visitors are not used by this test");
        }

        @Override
        public Element asElement() {
            return element;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return SimpleNoType.INSTANCE;
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return List.of();
        }
    }

    private enum SimpleNoType implements NoType {
        INSTANCE;

        @Override
        public TypeKind getKind() {
            return TypeKind.NONE;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return null;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException("Type visitors are not used by this test");
        }
    }

    private record SimpleName(String value) implements Name {
        @Override
        public boolean contentEquals(CharSequence characters) {
            return value.contentEquals(characters);
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
