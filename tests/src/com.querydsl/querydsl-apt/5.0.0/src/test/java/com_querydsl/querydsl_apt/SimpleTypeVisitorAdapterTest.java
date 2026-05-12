/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_apt;

import com.querydsl.apt.ExtendedTypeFactory;
import com.querydsl.codegen.TypeMappings;
import com.querydsl.codegen.utils.model.Type;
import com.querydsl.codegen.utils.model.TypeExtends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTypeVisitorAdapterTest {

    @TempDir
    Path outputDirectory;

    @Test
    void extendedTypeFactoryUsesFirstIntersectionBoundForTypeVariableUpperBound() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required to inspect javac intersection types").isNotNull();

        IntersectionTypeProcessor processor = new IntersectionTypeProcessor();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(this.outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:only"),
                    null,
                    List.of(new SourceFile("sample.IntersectionHolder", """
                            package sample;

                            class Base {
                            }

                            interface Named {
                            }

                            class IntersectionHolder<T extends Base & Named> {
                            }
                            """))
            );
            task.setProcessors(List.of(processor));

            assertThat(task.call()).as(formatDiagnostics(diagnostics)).isTrue();
        }

        assertThat(processor.failure).isNull();
        assertThat(processor.resolvedType).isInstanceOf(TypeExtends.class);
        TypeExtends extendedType = (TypeExtends) processor.resolvedType;
        assertThat(extendedType.getVarName()).isEqualTo("T");
        assertThat(extendedType.getFullName()).isEqualTo("sample.Base");
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

    private static final class IntersectionTypeProcessor extends AbstractProcessor {

        private Type resolvedType;

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
            if (this.resolvedType != null || roundEnv.processingOver()) {
                return false;
            }
            for (Element element : roundEnv.getRootElements()) {
                if (element instanceof TypeElement typeElement
                        && typeElement.getQualifiedName().contentEquals("sample.IntersectionHolder")) {
                    resolveTypeVariable(typeElement);
                    break;
                }
            }
            return false;
        }

        private void resolveTypeVariable(TypeElement typeElement) {
            try {
                ExtendedTypeFactory typeFactory = new ExtendedTypeFactory(
                        this.processingEnv,
                        Collections.emptySet(),
                        new TypeMappings() {
                        },
                        type -> type,
                        entityType -> entityType.getSimpleName()
                );
                TypeMirror typeVariable = typeElement.getTypeParameters().get(0).asType();
                this.resolvedType = typeFactory.getType(typeVariable, true);
            } catch (Throwable ex) {
                this.failure = ex;
            }
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
