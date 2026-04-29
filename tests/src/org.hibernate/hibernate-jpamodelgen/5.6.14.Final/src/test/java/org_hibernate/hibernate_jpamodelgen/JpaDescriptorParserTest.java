/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_jpamodelgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
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
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import org.hibernate.jpamodelgen.Context;
import org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor;
import org.hibernate.jpamodelgen.xml.JpaDescriptorParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JpaDescriptorParserTest {
    private static final String TIMESTAMP_CACHE_FILE_NAME = "Hibernate-Static-Metamodel-Generator.tmp";

    @TempDir
    Path temporaryDirectory;

    @Test
    void lazyXmlParsingWritesAndReadsTimestampCache() {
        String originalTemporaryDirectory = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaryDirectory.toString());
        try {
            parseWithLazyXmlParsing();
            assertThat(temporaryDirectory.resolve(TIMESTAMP_CACHE_FILE_NAME)).exists().isNotEmptyFile();

            parseWithLazyXmlParsing();
            assertThat(temporaryDirectory.resolve(TIMESTAMP_CACHE_FILE_NAME)).exists().isNotEmptyFile();
        }
        finally {
            System.setProperty("java.io.tmpdir", originalTemporaryDirectory);
        }
    }

    private static void parseWithLazyXmlParsing() {
        Context context = new Context(new TestProcessingEnvironment());
        new JpaDescriptorParser(context).parseXml();
        assertThat(context.getMetaEntities()).isEmpty();
        assertThat(context.getMetaEmbeddables()).isEmpty();
    }

    private static final class TestProcessingEnvironment implements ProcessingEnvironment {
        private final Map<String, String> options = new HashMap<String, String>();
        private final Messager messager = new TestMessager();
        private final Filer filer = new TestFiler();
        private final Elements elements = new TestElements();
        private final Types types = new TestTypes();

        private TestProcessingEnvironment() {
            options.put(JPAMetaModelEntityProcessor.LAZY_XML_PARSING, "true");
        }

        @Override
        public Map<String, String> getOptions() {
            return options;
        }

        @Override
        public Messager getMessager() {
            return messager;
        }

        @Override
        public Filer getFiler() {
            return filer;
        }

        @Override
        public Elements getElementUtils() {
            return elements;
        }

        @Override
        public Types getTypeUtils() {
            return types;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    private static final class TestMessager implements Messager {
        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence message,
                Element element,
                AnnotationMirror annotationMirror) {
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence message,
                Element element,
                AnnotationMirror annotationMirror,
                AnnotationValue annotationValue) {
        }
    }

    private static final class TestFiler implements Filer {
        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new IOException("Source file creation is not supported by this test filer");
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new IOException("Class file creation is not supported by this test filer");
        }

        @Override
        public FileObject createResource(
                JavaFileManager.Location location,
                CharSequence packageName,
                CharSequence relativeName,
                Element... originatingElements) throws IOException {
            throw new IOException("Resource creation is not supported by this test filer");
        }

        @Override
        public FileObject getResource(
                JavaFileManager.Location location,
                CharSequence packageName,
                CharSequence relativeName) throws IOException {
            throw new FileNotFoundException(relativeName.toString());
        }
    }

    private static final class TestElements implements Elements {
        @Override
        public PackageElement getPackageElement(CharSequence name) {
            return null;
        }

        @Override
        public TypeElement getTypeElement(CharSequence name) {
            return null;
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
                AnnotationMirror annotationMirror) {
            return Collections.emptyMap();
        }

        @Override
        public String getDocComment(Element element) {
            return null;
        }

        @Override
        public boolean isDeprecated(Element element) {
            return false;
        }

        @Override
        public Name getBinaryName(TypeElement type) {
            return new TestName(type.getSimpleName());
        }

        @Override
        public PackageElement getPackageOf(Element type) {
            return null;
        }

        @Override
        public List<? extends Element> getAllMembers(TypeElement type) {
            return Collections.emptyList();
        }

        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
            return Collections.emptyList();
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
            return String.valueOf(value);
        }

        @Override
        public void printElements(Writer writer, Element... elements) {
        }

        @Override
        public Name getName(CharSequence cs) {
            return new TestName(cs);
        }

        @Override
        public boolean isFunctionalInterface(TypeElement type) {
            return false;
        }
    }

    private static final class TestTypes implements Types {
        @Override
        public Element asElement(TypeMirror typeMirror) {
            return null;
        }

        @Override
        public boolean isSameType(TypeMirror typeMirror, TypeMirror otherTypeMirror) {
            return false;
        }

        @Override
        public boolean isSubtype(TypeMirror typeMirror, TypeMirror otherTypeMirror) {
            return false;
        }

        @Override
        public boolean isAssignable(TypeMirror typeMirror, TypeMirror otherTypeMirror) {
            return false;
        }

        @Override
        public boolean contains(TypeMirror typeMirror, TypeMirror otherTypeMirror) {
            return false;
        }

        @Override
        public boolean isSubsignature(ExecutableType executableType, ExecutableType otherExecutableType) {
            return false;
        }

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror typeMirror) {
            return Collections.emptyList();
        }

        @Override
        public TypeMirror erasure(TypeMirror typeMirror) {
            return typeMirror;
        }

        @Override
        public TypeElement boxedClass(PrimitiveType primitiveType) {
            return null;
        }

        @Override
        public PrimitiveType unboxedType(TypeMirror typeMirror) {
            return null;
        }

        @Override
        public TypeMirror capture(TypeMirror typeMirror) {
            return typeMirror;
        }

        @Override
        public PrimitiveType getPrimitiveType(TypeKind kind) {
            return null;
        }

        @Override
        public NullType getNullType() {
            return null;
        }

        @Override
        public NoType getNoType(TypeKind kind) {
            return null;
        }

        @Override
        public ArrayType getArrayType(TypeMirror componentType) {
            return null;
        }

        @Override
        public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {
            return null;
        }

        @Override
        public DeclaredType getDeclaredType(TypeElement typeElement, TypeMirror... typeArguments) {
            return null;
        }

        @Override
        public DeclaredType getDeclaredType(
                DeclaredType containingType,
                TypeElement typeElement,
                TypeMirror... typeArguments) {
            return null;
        }

        @Override
        public TypeMirror asMemberOf(DeclaredType containingType, Element element) {
            return null;
        }
    }

    private static final class TestName implements Name {
        private final String value;

        private TestName(CharSequence value) {
            this.value = value.toString();
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
