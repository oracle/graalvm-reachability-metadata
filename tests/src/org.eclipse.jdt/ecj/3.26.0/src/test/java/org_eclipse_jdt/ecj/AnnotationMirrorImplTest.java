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
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AnnotationMirrorImplTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void annotationProxyConvertsEnumAndNestedAnnotationMembers() throws IOException {
        final Path sourceDirectory = Files.createDirectories(this.temporaryDirectory.resolve("sources"));
        final Path classesDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);

        final JavaCompiler compiler = new EclipseCompiler();
        final AnnotationMirrorImplCoverageProcessor processor = new AnnotationMirrorImplCoverageProcessor();
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

            final Boolean result = callWithRecognizedClassFileVersion(task);

            assertThat(result).isTrue();
            assertThat(processor.hasVerifiedAnnotationValues()).isTrue();
        }
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path javaLangAnnotationDirectory = Files.createDirectories(javaLangDirectory.resolve("annotation"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path enumSource = javaLangDirectory.resolve("Enum.java");
        final Path annotationSource = javaLangAnnotationDirectory.resolve("Annotation.java");
        final Path testSource = sourceDirectory.resolve("AnnotationMirrorImplTest.java");
        Files.writeString(objectSource, javaLangObjectSource(), StandardCharsets.UTF_8);
        Files.writeString(classSource, javaLangClassSource(), StandardCharsets.UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), StandardCharsets.UTF_8);
        Files.writeString(enumSource, javaLangEnumSource(), StandardCharsets.UTF_8);
        Files.writeString(annotationSource, javaLangAnnotationSource(), StandardCharsets.UTF_8);
        Files.writeString(testSource, source(), StandardCharsets.UTF_8);
        return List.of(objectSource, classSource, stringSource, enumSource, annotationSource, testSource);
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

    private static String javaLangEnumSource() {
        return """
                package java.lang;

                public abstract class Enum<E extends Enum<E>> {
                    protected Enum(String name, int ordinal) {
                    }
                }
                """;
    }

    private static String javaLangAnnotationSource() {
        return """
                package java.lang.annotation;

                public interface Annotation {
                    java.lang.Class<? extends Annotation> annotationType();
                }
                """;
    }

    private static String source() {
        return """
                package org_eclipse_jdt.ecj;

                @AnnotationMirrorImplTest.MirrorCoverageAnnotation(
                    role = AnnotationMirrorImplTest.MirrorCoverageRole.WRITE,
                    roles = AnnotationMirrorImplTest.MirrorCoverageRole.ADMIN,
                    nested = @AnnotationMirrorImplTest.MirrorNestedAnnotation(name = "primary"),
                    nestedValues = @AnnotationMirrorImplTest.MirrorNestedAnnotation(name = "child"),
                    priorities = 7
                )
                final class MirrorCoverageTarget {
                }

                public final class AnnotationMirrorImplTest {
                    public @interface MirrorNestedAnnotation {
                        String name();
                    }

                    public enum MirrorCoverageRole {
                        ADMIN,
                        WRITE
                    }

                    public @interface MirrorCoverageAnnotation {
                        MirrorCoverageRole role();
                        MirrorCoverageRole[] roles();
                        MirrorNestedAnnotation nested();
                        MirrorNestedAnnotation[] nestedValues();
                        int[] priorities();
                    }
                }
                """;
    }

    public @interface MirrorNestedAnnotation {
        String name();
    }

    public enum MirrorCoverageRole {
        ADMIN,
        WRITE
    }

    public @interface MirrorCoverageAnnotation {
        MirrorCoverageRole role();

        MirrorCoverageRole[] roles();

        MirrorNestedAnnotation nested();

        MirrorNestedAnnotation[] nestedValues();

        int[] priorities();
    }

    private static final class AnnotationMirrorImplCoverageProcessor extends AbstractProcessor {
        private boolean verifiedAnnotationValues;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton(MirrorCoverageAnnotation.class.getCanonicalName());
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_6;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            for (Element elementAnnotationAccess : roundEnvironment.getElementsAnnotatedWith(MirrorCoverageAnnotation.class)) {
                final MirrorCoverageAnnotation annotation = elementAnnotationAccess.getAnnotation(MirrorCoverageAnnotation.class);
                assertThat(annotation).isNotNull();

                assertThat(annotation.role()).isEqualTo(MirrorCoverageRole.WRITE);
                assertThat(annotation.roles()).containsExactly(MirrorCoverageRole.ADMIN);
                assertThat(annotation.nested().name()).isEqualTo("primary");
                assertThat(annotation.nestedValues()).hasSize(1);
                assertThat(annotation.nestedValues()[0].name()).isEqualTo("child");
                assertThat(annotation.priorities()).containsExactly(7);
                this.verifiedAnnotationValues = true;
            }
            return false;
        }

        boolean hasVerifiedAnnotationValues() {
            return this.verifiedAnnotationValues;
        }
    }
}
