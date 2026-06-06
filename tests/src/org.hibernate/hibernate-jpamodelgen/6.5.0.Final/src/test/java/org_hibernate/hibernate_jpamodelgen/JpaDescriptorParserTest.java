/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_jpamodelgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import org.hibernate.jpamodelgen.Context;
import org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor;
import org.hibernate.jpamodelgen.util.FileTimeStampChecker;
import org.hibernate.jpamodelgen.xml.JpaDescriptorParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JpaDescriptorParserTest {
    private static final String CACHE_FILE_NAME = "Hibernate-Static-Metamodel-Generator.tmp";

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesWithLazyXmlTimestampCache() throws IOException {
        String originalTemporaryDirectory = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaryDirectory.toString());
        try {
            Path cacheFile = temporaryDirectory.resolve(CACHE_FILE_NAME);
            writeStaleTimestampCache(cacheFile);

            Context context = new Context(new MinimalProcessingEnvironment());
            JpaDescriptorParser parser = new JpaDescriptorParser(context);

            parser.parseXml();

            assertTrue(Files.exists(cacheFile));
        } finally {
            restoreTemporaryDirectory(originalTemporaryDirectory);
        }
    }

    private static void restoreTemporaryDirectory(String originalTemporaryDirectory) {
        if (originalTemporaryDirectory == null) {
            System.clearProperty("java.io.tmpdir");
        } else {
            System.setProperty("java.io.tmpdir", originalTemporaryDirectory);
        }
    }

    private static void writeStaleTimestampCache(Path cacheFile) throws IOException {
        FileTimeStampChecker fileTimeStampChecker = new FileTimeStampChecker();
        fileTimeStampChecker.add("/mapping-file-that-is-not-on-the-classpath.xml", 1L);
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(cacheFile))) {
            outputStream.writeObject(fileTimeStampChecker);
        }
    }

    private static final class MinimalProcessingEnvironment implements ProcessingEnvironment {
        private final Elements elements = new MinimalElements();
        private final Filer filer = new NoOpFiler();
        private final Messager messager = new NoOpMessager();

        @Override
        public Map<String, String> getOptions() {
            return Map.of(JPAMetaModelEntityProcessor.LAZY_XML_PARSING, Boolean.TRUE.toString());
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
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.getDefault();
        }
    }

    private static final class NoOpFiler implements Filer {
        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new IOException("Source file creation is not used by this test");
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new IOException("Class file creation is not used by this test");
        }

        @Override
        public FileObject createResource(
                JavaFileManager.Location location,
                CharSequence moduleAndPkg,
                CharSequence relativeName,
                Element... originatingElements) throws IOException {
            throw new IOException("Resource creation is not used by this test");
        }

        @Override
        public FileObject getResource(
                JavaFileManager.Location location,
                CharSequence moduleAndPkg,
                CharSequence relativeName) throws IOException {
            throw new IOException("No generated resources are available in this test");
        }
    }

    private static final class NoOpMessager implements Messager {
        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence msg,
                Element e,
                AnnotationMirror a,
                AnnotationValue v) {
        }
    }

    private static final class MinimalElements implements Elements {
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
                AnnotationMirror a) {
            return Collections.emptyMap();
        }

        @Override
        public String getDocComment(Element e) {
            return null;
        }

        @Override
        public boolean isDeprecated(Element e) {
            return false;
        }

        @Override
        public Name getBinaryName(TypeElement type) {
            return new SimpleName(type.getQualifiedName().toString());
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
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
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
        public void printElements(Writer w, Element... elements) {
        }

        @Override
        public Name getName(CharSequence cs) {
            return new SimpleName(cs.toString());
        }

        @Override
        public boolean isFunctionalInterface(TypeElement type) {
            return false;
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
