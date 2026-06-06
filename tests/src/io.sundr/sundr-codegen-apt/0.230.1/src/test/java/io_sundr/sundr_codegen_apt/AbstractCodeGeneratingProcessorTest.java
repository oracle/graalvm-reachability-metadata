/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_apt;

import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import io.sundr.codegen.apt.processor.AbstractCodeGeneratingProcessor;
import io.sundr.model.TypeDef;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCodeGeneratingProcessorTest {

    @Test
    void generateSkipsTypesThatAlreadyExistOnTheClassPath() {
        TestCodeGeneratingProcessor processor = new TestCodeGeneratingProcessor();
        processor.init(new MinimalProcessingEnvironment());

        processor.generate(TypeDef.forName("java.lang.String"));

        assertThat(processor.getAptContext()).isNotNull();
        assertThat(processor.getAdapterContext()).isNotNull();
        assertThat(processor.getDefinitionRepository()).isNotNull();
    }

    private static final class TestCodeGeneratingProcessor extends AbstractCodeGeneratingProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
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
            return new MinimalElements();
        }

        @Override
        public Types getTypeUtils() {
            return new MinimalTypes();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    private static final class MinimalElements implements Elements {
        @Override
        public PackageElement getPackageElement(CharSequence name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeElement getTypeElement(CharSequence name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
                AnnotationMirror annotationMirror) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDocComment(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDeprecated(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name getBinaryName(TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackageElement getPackageOf(Element type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends Element> getAllMembers(TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hides(Element hider, Element hidden) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getConstantExpression(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void printElements(Writer writer, Element... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name getName(CharSequence characters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFunctionalInterface(TypeElement type) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MinimalTypes implements Types {
        @Override
        public Element asElement(TypeMirror type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSameType(TypeMirror first, TypeMirror second) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSubtype(TypeMirror first, TypeMirror second) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAssignable(TypeMirror first, TypeMirror second) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(TypeMirror first, TypeMirror second) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSubsignature(ExecutableType first, ExecutableType second) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror erasure(TypeMirror type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeElement boxedClass(PrimitiveType type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrimitiveType unboxedType(TypeMirror type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror capture(TypeMirror type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrimitiveType getPrimitiveType(TypeKind kind) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NullType getNullType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NoType getNoType(TypeKind kind) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArrayType getArrayType(TypeMirror componentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeclaredType getDeclaredType(TypeElement type, TypeMirror... typeArguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeclaredType getDeclaredType(DeclaredType containing, TypeElement type, TypeMirror... typeArguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror asMemberOf(DeclaredType containing, Element element) {
            throw new UnsupportedOperationException();
        }
    }
}
