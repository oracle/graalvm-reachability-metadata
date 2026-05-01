/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NoTypeImplTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void getAnnotationsByTypeReturnsTypedEmptyArrayForVoidNoType() throws IOException {
        final Path sourceDirectory = Files.createDirectories(this.temporaryDirectory.resolve("sources"));
        final Path classesDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);
        final NoTypeImplCoverageProcessor processor = new NoTypeImplCoverageProcessor();
        final JavaCompiler compiler = new EclipseCompiler();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                    Collections.singletonList(classesDirectory.toFile()));
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    sourceFiles.stream().map(Path::toFile).toList());
            final List<String> options = List.of("-proc:only", "-source", "1.6", "-target", "1.6");
            final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null,
                    compilationUnits);
            task.setProcessors(Collections.singletonList(processor));

            final Boolean result = callWithRecognizedClassFileVersion(task);

            assertThat(result).isTrue();
            assertThat(processor.hasVerifiedNoTypeAnnotations()).isTrue();
        }
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path targetDirectory = Files.createDirectories(sourceDirectory.resolve("org_eclipse_jdt/ecj/coverage"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path targetSource = targetDirectory.resolve("NoTypeImplCoverageTarget.java");
        Files.writeString(objectSource, javaLangObjectSource(), UTF_8);
        Files.writeString(classSource, javaLangClassSource(), UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), UTF_8);
        Files.writeString(targetSource, source(), UTF_8);
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

                public final class NoTypeImplCoverageTarget {
                }
                """;
    }

    public @interface NoTypeMarker {
    }

    private static final class NoTypeImplCoverageProcessor extends AbstractProcessor {
        private boolean verifiedNoTypeAnnotations;

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
            if (roundEnvironment.processingOver() || this.verifiedNoTypeAnnotations) {
                return false;
            }
            final NoType voidType = this.processingEnv.getTypeUtils().getNoType(TypeKind.VOID);
            assertThat(voidType.getKind()).isEqualTo(TypeKind.VOID);

            final NoTypeMarker[] markers = voidType.getAnnotationsByType(NoTypeMarker.class);

            assertThat(markers).isEmpty();
            this.verifiedNoTypeAnnotations = true;
            return false;
        }

        boolean hasVerifiedNoTypeAnnotations() {
            return this.verifiedNoTypeAnnotations;
        }
    }
}
