/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.codehaus.commons.compiler.AbstractCompilerFactory;
import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.Cookable;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.WarningHandler;
import org.junit.jupiter.api.Test;

public class CompilerFactoryFactoryTest {

    @Test
    void loadsCompilerFactoriesFromContextClassLoaderResources() throws Exception {
        try (ContextClassLoaderScope ignored = withCompilerFactoryResources(2)) {
            ICompilerFactory defaultFactory = CompilerFactoryFactory.getDefaultCompilerFactory();
            ICompilerFactory directFactory = CompilerFactoryFactory.getCompilerFactory(TestCompilerFactory.class.getName());
            ICompilerFactory[] allFactories = CompilerFactoryFactory.getAllCompilerFactories();

            assertThat(defaultFactory).isInstanceOf(TestCompilerFactory.class);
            assertThat(directFactory).isInstanceOf(TestCompilerFactory.class);
            assertThat(allFactories).hasSize(2);
            for (ICompilerFactory factory : allFactories) {
                assertThat(factory).isInstanceOf(TestCompilerFactory.class);
            }
        }
    }

    public static ContextClassLoaderScope withCompilerFactoryResources(int resourceCount) throws IOException {
        return new ContextClassLoaderScope(resourceCount);
    }

    public static final class ContextClassLoaderScope implements AutoCloseable {
        private final ClassLoader originalContextClassLoader;
        private final List<Path> resources;

        private ContextClassLoaderScope(int resourceCount) throws IOException {
            this.originalContextClassLoader = Thread.currentThread().getContextClassLoader();
            this.resources = createCompilerFactoryResources(resourceCount);
            Thread.currentThread().setContextClassLoader(
                    new CompilerFactoryResourceClassLoader(originalContextClassLoader, resources)
            );
        }

        @Override
        public void close() throws IOException {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            for (Path resource : resources) {
                Files.deleteIfExists(resource);
            }
        }
    }

    private static List<Path> createCompilerFactoryResources(int resourceCount) throws IOException {
        ArrayList<Path> resources = new ArrayList<>();
        String properties = "compilerFactory=" + TestCompilerFactory.class.getName() + System.lineSeparator();
        for (int i = 0; i < resourceCount; i++) {
            Path resource = Files.createTempFile("commons-compiler-", ".properties");
            Files.writeString(resource, properties, StandardCharsets.ISO_8859_1);
            resources.add(resource);
        }
        return resources;
    }

    public static final class CompilerFactoryResourceClassLoader extends ClassLoader {
        private static final String RESOURCE_NAME = "org.codehaus.commons.compiler.properties";

        private final List<Path> resources;

        public CompilerFactoryResourceClassLoader(ClassLoader parent, List<Path> resources) {
            super(parent);
            this.resources = resources;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!RESOURCE_NAME.equals(name)) {
                return super.getResourceAsStream(name);
            }

            try {
                return Files.newInputStream(resources.get(0));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!RESOURCE_NAME.equals(name)) {
                return super.getResources(name);
            }

            ArrayList<URL> urls = new ArrayList<>(resources.size());
            for (Path resource : resources) {
                urls.add(resource.toUri().toURL());
            }
            return Collections.enumeration(urls);
        }
    }

    public static final class TestCompilerFactory extends AbstractCompilerFactory {

        @Override
        public String getId() {
            return "test-compiler-factory";
        }

        @Override
        public String toString() {
            return getId();
        }

        @Override
        public String getImplementationVersion() {
            return "test";
        }

        @Override
        public IClassBodyEvaluator newClassBodyEvaluator() {
            return new TestClassBodyEvaluator();
        }

        @Override
        public ICompiler newCompiler() {
            throw new UnsupportedOperationException("Not used by these tests");
        }

        @Override
        public AbstractJavaSourceClassLoader newJavaSourceClassLoader() {
            return new TestJavaSourceClassLoader(TestJavaSourceClassLoader.class.getClassLoader());
        }

        @Override
        public AbstractJavaSourceClassLoader newJavaSourceClassLoader(ClassLoader parent) {
            return new TestJavaSourceClassLoader(parent);
        }
    }

    public static final class TestJavaSourceClassLoader extends AbstractJavaSourceClassLoader {

        public TestJavaSourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public void setSourcePath(File[] sourcePath) {
        }

        @Override
        public void setSourceFileCharacterEncoding(String optionalCharacterEncoding) {
        }

        @Override
        public void setDebuggingInfo(boolean lines, boolean vars, boolean source) {
        }
    }

    public static final class JavaSourceMain {
        private static String[] arguments = new String[0];

        public static void reset() {
            arguments = new String[0];
        }

        public static String[] arguments() {
            return arguments;
        }

        public static void main(String[] args) {
            arguments = args;
        }
    }

    public static final class EvaluatedClassBodyMain {
        private static String[] arguments = new String[0];

        public static void reset() {
            arguments = new String[0];
            TestClassBodyEvaluator.reset();
        }

        public static String[] arguments() {
            return arguments;
        }

        public static void main(String[] args) {
            arguments = args;
        }
    }

    public static final class TestClassBodyEvaluator extends Cookable implements IClassBodyEvaluator {
        private static String cookedSource = "";

        public static void reset() {
            cookedSource = "";
        }

        public static String cookedSource() {
            return cookedSource;
        }

        @Override
        public void setParentClassLoader(ClassLoader parentClassLoader) {
        }

        @Override
        public void setDebuggingInformation(boolean debugSource, boolean debugLines, boolean debugVars) {
        }

        @Override
        public void cook(String optionalFileName, Reader reader) throws CompileException, IOException {
            cookedSource = Cookable.readString(reader);
        }

        @Override
        public void setCompileErrorHandler(ErrorHandler compileErrorHandler) {
        }

        @Override
        public void setWarningHandler(WarningHandler warningHandler) {
        }

        @Override
        public ClassLoader getClassLoader() {
            return EvaluatedClassBodyMain.class.getClassLoader();
        }

        @Override
        public void setPermissions(Permissions permissions) {
        }

        @Override
        public void setNoPermissions() {
        }

        @Override
        public void setDefaultImports(String... defaultImports) {
        }

        @Override
        public void setClassName(String className) {
        }

        @Override
        public void setExtendedClass(Class<?> extendedClass) {
        }

        @Override
        public void setExtendedType(Class<?> extendedType) {
        }

        @Override
        public void setImplementedInterfaces(Class<?>[] implementedInterfaces) {
        }

        @Override
        public void setImplementedTypes(Class<?>[] implementedTypes) {
        }

        @Override
        public Class<?> getClazz() {
            return EvaluatedClassBodyMain.class;
        }

        @Override
        public Object createInstance(Reader reader) throws CompileException, IOException {
            cook(reader);
            try {
                return getClazz().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
