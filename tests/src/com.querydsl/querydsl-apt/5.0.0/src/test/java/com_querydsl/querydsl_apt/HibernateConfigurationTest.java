/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_apt;

import com.querydsl.apt.hibernate.HibernateConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class HibernateConfigurationTest {

    @TempDir
    Path outputDirectory;

    @Test
    void includesHibernateSpecificAnnotations() throws Exception {
        NativeCompilerSupport.ensureJavaHomeProperty();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required to create an annotation processing environment").isNotNull();

        HibernateConfigurationProcessor processor = new HibernateConfigurationProcessor();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(this.outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:only"),
                    null,
                    List.of(new SourceFile("sample.Entity", """
                            package sample;

                            class Entity {
                            }
                            """))
            );
            task.setProcessors(List.of(processor));

            assertThat(task.call()).as(formatDiagnostics(diagnostics)).isTrue();
        }

        assertThat(processor.failure).isNull();
        assertThat(processor.annotationNames)
                .contains(
                        "org.hibernate.annotations.Type",
                        "org.hibernate.annotations.Cascade",
                        "org.hibernate.annotations.LazyCollection",
                        "org.hibernate.annotations.OnDelete"
                );
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder result = new StringBuilder("Compilation diagnostics");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            result.append(System.lineSeparator())
                    .append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(null));
        }
        return result.toString();
    }

    private @interface EntityAnnotation {
    }

    private @interface SuperTypeAnnotation {
    }

    private @interface EmbeddableAnnotation {
    }

    private @interface EmbeddedAnnotation {
    }

    private @interface SkipAnnotation {
    }

    private static final class HibernateConfigurationProcessor extends AbstractProcessor {

        private Set<String> annotationNames;

        private Throwable failure;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (this.annotationNames != null || roundEnv.processingOver()) {
                return false;
            }
            try {
                ExposedHibernateConfiguration configuration = new ExposedHibernateConfiguration(
                        roundEnv,
                        this.processingEnv,
                        EntityAnnotation.class,
                        SuperTypeAnnotation.class,
                        EmbeddableAnnotation.class,
                        EmbeddedAnnotation.class,
                        SkipAnnotation.class
                );
                this.annotationNames = configuration.exposedAnnotations().stream()
                        .map(Class::getName)
                        .collect(Collectors.toSet());
            } catch (Throwable ex) {
                this.failure = ex;
            }
            return false;
        }

    }

    private static final class ExposedHibernateConfiguration extends HibernateConfiguration {

        private ExposedHibernateConfiguration(
                RoundEnvironment roundEnv,
                ProcessingEnvironment processingEnv,
                Class<? extends Annotation> entityAnn,
                Class<? extends Annotation> superTypeAnn,
                Class<? extends Annotation> embeddableAnn,
                Class<? extends Annotation> embeddedAnn,
                Class<? extends Annotation> skipAnn) throws ClassNotFoundException {
            super(roundEnv, processingEnv, entityAnn, superTypeAnn, embeddableAnn, embeddedAnn, skipAnn);
        }

        private List<Class<? extends Annotation>> exposedAnnotations() {
            return getAnnotations();
        }

    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String content;

        private SourceFile(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.content;
        }

    }

}
