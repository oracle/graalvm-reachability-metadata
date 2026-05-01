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
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TypeParameterElementImplTest {
    private static final String TARGET_CLASS_NAME = "org_eclipse_jdt.ecj.coverage.TypeParameterCoverageTarget";

    @TempDir
    Path temporaryDirectory;

    @Test
    void repeatedTypeParameterAnnotationReturnsTypedEmptyArrayForJavacCompatibility() throws IOException {
        final Path sourceDirectory = Files.createDirectories(this.temporaryDirectory.resolve("sources"));
        final Path classesDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);

        final JavaCompiler compiler = new EclipseCompiler();
        final TypeParameterElementImplCoverageProcessor processor = new TypeParameterElementImplCoverageProcessor();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                    Collections.singletonList(classesDirectory.toFile()));
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    sourceFiles.stream().map(Path::toFile).toList());
            final List<String> options = List.of("-proc:only", "-source", "1.8", "-target", "1.8");
            final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null,
                    compilationUnits);
            task.setProcessors(Collections.singletonList(processor));

            final Boolean result = callWithRecognizedClassFileVersion(task);

            assertThat(result).isTrue();
            assertThat(processor.hasVerifiedRepeatedTypeParameterAnnotations()).isTrue();
        }
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path javaLangAnnotationDirectory = Files.createDirectories(javaLangDirectory.resolve("annotation"));
        final Path targetDirectory = Files.createDirectories(sourceDirectory.resolve("org_eclipse_jdt/ecj/coverage"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path enumSource = javaLangDirectory.resolve("Enum.java");
        final Path annotationSource = javaLangAnnotationDirectory.resolve("Annotation.java");
        final Path elementTypeSource = javaLangAnnotationDirectory.resolve("ElementType.java");
        final Path repeatableSource = javaLangAnnotationDirectory.resolve("Repeatable.java");
        final Path targetAnnotationSource = javaLangAnnotationDirectory.resolve("Target.java");
        final Path testSource = sourceDirectory.resolve("TypeParameterElementImplTest.java");
        final Path targetSource = targetDirectory.resolve("TypeParameterCoverageTarget.java");
        Files.writeString(objectSource, javaLangObjectSource(), UTF_8);
        Files.writeString(classSource, javaLangClassSource(), UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), UTF_8);
        Files.writeString(enumSource, javaLangEnumSource(), UTF_8);
        Files.writeString(annotationSource, javaLangAnnotationSource(), UTF_8);
        Files.writeString(elementTypeSource, javaLangAnnotationElementTypeSource(), UTF_8);
        Files.writeString(repeatableSource, javaLangAnnotationRepeatableSource(), UTF_8);
        Files.writeString(targetAnnotationSource, javaLangAnnotationTargetSource(), UTF_8);
        Files.writeString(testSource, source(), UTF_8);
        Files.writeString(targetSource, targetSource(), UTF_8);
        return List.of(objectSource, classSource, stringSource, enumSource, annotationSource, elementTypeSource,
                repeatableSource, targetAnnotationSource, testSource, targetSource);
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

    private static String javaLangAnnotationElementTypeSource() {
        return """
                package java.lang.annotation;

                public enum ElementType {
                    ANNOTATION_TYPE,
                    TYPE_PARAMETER
                }
                """;
    }

    private static String javaLangAnnotationRepeatableSource() {
        return """
                package java.lang.annotation;

                public @interface Repeatable {
                    java.lang.Class<? extends Annotation> value();
                }
                """;
    }

    private static String javaLangAnnotationTargetSource() {
        return """
                package java.lang.annotation;

                public @interface Target {
                    ElementType[] value();
                }
                """;
    }

    private static String source() {
        return """
                package org_eclipse_jdt.ecj;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Repeatable;
                import java.lang.annotation.Target;

                public final class TypeParameterElementImplTest {
                    @Target(ElementType.TYPE_PARAMETER)
                    @Repeatable(TypeParameterMarkers.class)
                    public @interface TypeParameterMarker {
                    }

                    @Target(ElementType.TYPE_PARAMETER)
                    public @interface TypeParameterMarkers {
                        TypeParameterMarker[] value();
                    }
                }
                """;
    }

    private static String targetSource() {
        return """
                package org_eclipse_jdt.ecj.coverage;

                import org_eclipse_jdt.ecj.TypeParameterElementImplTest.TypeParameterMarker;

                public final class TypeParameterCoverageTarget<@TypeParameterMarker @TypeParameterMarker T> {
                }
                """;
    }

    @Target(ElementType.TYPE_PARAMETER)
    @Repeatable(TypeParameterMarkers.class)
    public @interface TypeParameterMarker {
    }

    @Target(ElementType.TYPE_PARAMETER)
    public @interface TypeParameterMarkers {
        TypeParameterMarker[] value();
    }

    private static final class TypeParameterElementImplCoverageProcessor extends AbstractProcessor {
        private boolean verifiedRepeatedTypeParameterAnnotations;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_8;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (roundEnvironment.processingOver() || this.verifiedRepeatedTypeParameterAnnotations) {
                return false;
            }
            final TypeElement targetElement = this.processingEnv.getElementUtils().getTypeElement(TARGET_CLASS_NAME);
            assertThat(targetElement).isNotNull();
            assertThat(targetElement.getTypeParameters()).hasSize(1);

            final TypeParameterElement typeParameter = targetElement.getTypeParameters().get(0);
            assertThat(typeParameter.getKind()).isEqualTo(ElementKind.TYPE_PARAMETER);

            final TypeParameterMarker[] annotationsByType = typeParameter.getAnnotationsByType(
                    TypeParameterMarker.class);

            assertThat(annotationsByType).isEmpty();
            assertThat(annotationsByType.getClass().getComponentType()).isEqualTo(TypeParameterMarker.class);
            this.verifiedRepeatedTypeParameterAnnotations = true;
            return false;
        }

        boolean hasVerifiedRepeatedTypeParameterAnnotations() {
            return this.verifiedRepeatedTypeParameterAnnotations;
        }
    }
}
