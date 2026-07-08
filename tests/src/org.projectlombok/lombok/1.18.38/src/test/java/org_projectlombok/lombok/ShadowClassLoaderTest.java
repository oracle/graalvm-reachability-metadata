/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

@TestMethodOrder(OrderAnnotation.class)
public class ShadowClassLoaderTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    @Order(1)
    void serviceLoaderDiscoversLombokAnnotationProcessor() {
        List<Processor> processors = ServiceLoader.load(Processor.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        List<String> processorClassNames = processors.stream()
                .map(processor -> processor.getClass().getName())
                .toList();

        assertThat(processorClassNames)
                .contains("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
        assertThat(processors)
                .anySatisfy(processor -> {
                    assertThat(processor.getSupportedAnnotationTypes()).contains("*");
                    assertThat(processor.getSupportedSourceVersion()).isNotNull();
                });
    }

    @Test
    @Order(2)
    void commandLineEntryPointInstallsShadowClassLoaderAsContextClassLoader() throws Throwable {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            invokeLombokCommand("version");

            ClassLoader shadowClassLoader = Thread.currentThread().getContextClassLoader();
            assertThat(shadowClassLoader).isNotSameAs(originalContextClassLoader);

            List<URL> shadowedClassResources = Collections.list(
                    shadowClassLoader.getResources("lombok/core/AnnotationProcessor.class"));
            assertThat(shadowedClassResources).isNotEmpty();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    @Order(3)
    void javaCompilerRunsLombokAnnotationProcessorFromClasspath() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        Path sourceDirectory = temporaryDirectory.resolve("src/example");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);

        Path source = sourceDirectory.resolve("Person.java");
        Files.writeString(source, """
                package example;

                import lombok.Getter;

                public class Person {
                    @Getter
                    private final String name;

                    public Person(String name) {
                        this.name = name;
                    }
                }

                class UsesGeneratedGetter {
                    String read(Person person) {
                        return person.getName();
                    }
                }
                """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjects(source);
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
                    "-d", classesDirectory.toString());

            Boolean successful = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits).call();

            assertThat(successful)
                    .describedAs(formatDiagnostics(diagnostics))
                    .isTrue();
        }
        assertThat(classesDirectory.resolve("example/Person.class")).exists();
        assertThat(classesDirectory.resolve("example/UsesGeneratedGetter.class")).exists();
    }

    @Test
    @Order(4)
    void overrideClasspathSkipsSelfAndDelegatesToPrependedParent() throws Throwable {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            invokeLombokCommand("version");
            ClassLoader shadowClassLoader = Thread.currentThread().getContextClassLoader();

            Path emptyOverride = Files.createDirectories(
                    temporaryDirectory.resolve("empty-override"));
            invokeClassLoaderMethod(
                    shadowClassLoader,
                    "addOverrideClasspathEntry",
                    String.class,
                    emptyOverride.toString());

            assertThat(shadowClassLoader.getResource(
                    "lombok/launch/ShadowClassLoader.class")).isNull();

            ClassLoader prependedParent = new ClassLoader(null) {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    if ("example.ParentOnly".equals(name)) {
                        return String.class;
                    }
                    throw new ClassNotFoundException(name);
                }
            };
            invokeClassLoaderMethod(
                    shadowClassLoader, "prependParent", ClassLoader.class, prependedParent);

            assertThat(shadowClassLoader.loadClass("example.ParentOnly")).isSameAs(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private void invokeLombokCommand(String command) throws Throwable {
        Class<?> mainClass = Class.forName("lombok.launch.Main");
        Method main = mainClass.getDeclaredMethod("main", String[].class);
        main.setAccessible(true);
        try {
            main.invoke(null, (Object) new String[] {command});
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private void invokeClassLoaderMethod(
            ClassLoader shadowClassLoader,
            String name,
            Class<?> parameterType,
            Object argument) throws Throwable {
        Method method = shadowClassLoader.getClass().getMethod(name, parameterType);
        method.setAccessible(true);
        try {
            method.invoke(shadowClassLoader, argument);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            message.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return message.toString();
    }
}
