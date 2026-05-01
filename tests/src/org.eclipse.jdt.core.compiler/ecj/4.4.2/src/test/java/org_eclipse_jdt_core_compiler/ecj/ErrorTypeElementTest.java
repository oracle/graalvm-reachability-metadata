/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ErrorTypeElementTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingSuperclassElementReturnsEmptyAnnotationArrayByType() throws IOException {
        final Path sourceDirectory = Files.createDirectories(this.temporaryDirectory.resolve("sources"));
        final Path classesDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);

        final JavaCompiler compiler = new EclipseCompiler();
        final ErrorTypeElementCoverageProcessor processor = new ErrorTypeElementCoverageProcessor();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null,
                StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                    Collections.singletonList(classesDirectory.toFile()));
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    sourceFiles.stream().map(Path::toFile).toList());
            final List<String> options = List.of("-proc:only", "-source", "1.6", "-target", "1.6");
            final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null,
                    compilationUnits);
            task.setProcessors(Collections.singletonList(processor));

            callWithRecognizedClassFileVersion(task);

            assertThat(processor.hasVerifiedMissingSuperclassElement()).isTrue();
        }
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path testSource = sourceDirectory.resolve("ErrorTypeElementTarget.java");
        Files.writeString(objectSource, javaLangObjectSource(), StandardCharsets.UTF_8);
        Files.writeString(classSource, javaLangClassSource(), StandardCharsets.UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), StandardCharsets.UTF_8);
        Files.writeString(testSource, source(), StandardCharsets.UTF_8);
        return List.of(objectSource, classSource, stringSource, testSource);
    }

    private static void callWithRecognizedClassFileVersion(JavaCompiler.CompilationTask task) {
        final String previousVersion = System.getProperty("java.class.version");
        System.setProperty("java.class.version", "52.0");
        try {
            task.call();
        } finally {
            if (previousVersion == null) {
                System.clearProperty("java.class.version");
            } else {
                System.setProperty("java.class.version", previousVersion);
            }
        }
    }

    private static String javaLangObjectSource() {
        return """
                package java.lang;

                public class Object {
                    public Object() {
                    }
                }
                """;
    }

    private static String javaLangClassSource() {
        return """
                package java.lang;

                public final class Class<T> {
                }
                """;
    }

    private static String javaLangStringSource() {
        return """
                package java.lang;

                public final class String {
                }
                """;
    }

    private static String source() {
        return """
                package org_eclipse_jdt_core_compiler.ecj.coverage;

                public final class ErrorTypeElementTarget extends missing.MissingBase {
                }
                """;
    }

    public @interface MissingTypeMarker {
    }

    private static final class ErrorTypeElementCoverageProcessor extends AbstractProcessor {
        private boolean verifiedMissingSuperclassElement;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_6;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            for (Element element : roundEnvironment.getRootElements()) {
                if ("ErrorTypeElementTarget".contentEquals(element.getSimpleName())) {
                    final TypeMirror superclass = ((TypeElement) element).getSuperclass();
                    assertThat(superclass.getKind()).isEqualTo(TypeKind.ERROR);

                    final Element missingSuperclass = ((DeclaredType) superclass).asElement();
                    assertThat(missingSuperclass.getKind()).isEqualTo(ElementKind.CLASS);
                    assertThat(missingSuperclass.getSimpleName()).hasToString("MissingBase");

                    final MissingTypeMarker[] annotationsByType = missingSuperclass
                            .getAnnotationsByType(MissingTypeMarker.class);
                    assertThat(annotationsByType).isEmpty();
                    this.verifiedMissingSuperclassElement = true;
                }
            }
            return false;
        }

        boolean hasVerifiedMissingSuperclassElement() {
            return this.verifiedMissingSuperclassElement;
        }
    }
}
