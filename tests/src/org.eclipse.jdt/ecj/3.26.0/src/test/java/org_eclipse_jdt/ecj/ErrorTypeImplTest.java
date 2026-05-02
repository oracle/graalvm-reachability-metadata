/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ErrorTypeImplTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingSuperclassTypeReturnsEmptyAnnotationArrayByType() throws IOException {
        final Path sourceDirectory = Files.createDirectories(this.temporaryDirectory.resolve("sources"));
        final Path classesDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);
        final JavaCompiler compiler = EcjTestSupport.newCompiler();
        final ErrorTypeImplCoverageProcessor processor = new ErrorTypeImplCoverageProcessor();

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

            assertThat(processor.hasVerifiedErrorType()).isTrue();
        }
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path targetDirectory = Files.createDirectories(sourceDirectory.resolve("org_eclipse_jdt/ecj/coverage"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path targetSource = targetDirectory.resolve("ErrorTypeImplTarget.java");
        Files.writeString(objectSource, javaLangObjectSource(), StandardCharsets.UTF_8);
        Files.writeString(classSource, javaLangClassSource(), StandardCharsets.UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), StandardCharsets.UTF_8);
        Files.writeString(targetSource, source(), StandardCharsets.UTF_8);
        return List.of(objectSource, classSource, stringSource, targetSource);
    }

    private static Boolean callWithRecognizedClassFileVersion(JavaCompiler.CompilationTask task) {
        final String previousVersion = System.getProperty("java.class.version");
        System.setProperty("java.class.version", "52.0");
        try {
            return task.call();
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
                package org_eclipse_jdt.ecj.coverage;

                public final class ErrorTypeImplTarget extends missing.UnresolvedBase {
                }
                """;
    }

    public @interface ErrorTypeMarker {
    }

    private static final class ErrorTypeImplCoverageProcessor extends AbstractProcessor {
        private boolean verifiedErrorType;

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
            for (Element rootElement : roundEnvironment.getRootElements()) {
                if ("ErrorTypeImplTarget".contentEquals(rootElement.getSimpleName())) {
                    verifyMissingSuperclassAnnotations((TypeElement) rootElement);
                    this.verifiedErrorType = true;
                }
            }
            return false;
        }

        private static void verifyMissingSuperclassAnnotations(TypeElement targetElement) {
            final TypeMirror superclass = targetElement.getSuperclass();
            assertThat(superclass.getKind()).isEqualTo(TypeKind.ERROR);

            final ErrorTypeMarker[] annotations = superclass.getAnnotationsByType(ErrorTypeMarker.class);
            assertThat(annotations).isEmpty();
        }

        boolean hasVerifiedErrorType() {
            return this.verifiedErrorType;
        }
    }
}
