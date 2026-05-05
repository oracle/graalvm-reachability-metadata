/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.Permissions;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.WarningHandler;
import org.codehaus.commons.compiler.samples.ClassBodyDemo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassBodyDemoTest {
    private static final String PROPERTIES_RESOURCE_NAME = "org.codehaus.commons.compiler.properties";
    private static final byte[] FACTORY_CONFIGURATION = ("compilerFactory="
            + CompilerFactoryFactoryTest.TestCompilerFactory.class.getName() + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void invokesCompiledClassBodyMainMethod() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            Thread.currentThread().setContextClassLoader(new ResourceBackedClassLoader(originalClassLoader));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));

            ClassBodyDemo.main(new String[] {
                "public static String main(String[] args) { return args[0]; }",
                "janino"
            });
        } finally {
            System.setOut(originalOut);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).isEqualTo("janino" + System.lineSeparator());
    }

    public static final class ReturningMainClass {
        private ReturningMainClass() {
        }

        public static String main(String[] arguments) {
            return arguments[0];
        }
    }

    public static final class TestClassBodyEvaluator implements IClassBodyEvaluator {
        private boolean cooked;

        @Override
        public void setDefaultImports(String... optionalDefaultImports) {
        }

        @Override
        public void setClassName(String className) {
        }

        @Override
        public void setExtendedClass(Class<?> optionalExtendedClass) {
        }

        @Deprecated
        @Override
        public void setExtendedType(Class<?> optionalExtendedClass) {
            setExtendedClass(optionalExtendedClass);
        }

        @Override
        public void setImplementedInterfaces(Class<?>[] implementedInterfaces) {
        }

        @Deprecated
        @Override
        public void setImplementedTypes(Class<?>[] implementedInterfaces) {
            setImplementedInterfaces(implementedInterfaces);
        }

        @Override
        public Class<?> getClazz() {
            if (!cooked) {
                throw new IllegalStateException("Class body was not cooked");
            }
            return ReturningMainClass.class;
        }

        @Override
        public Object createInstance(Reader reader) {
            throw unsupported("createInstance");
        }

        @Override
        public ClassLoader getClassLoader() {
            return ReturningMainClass.class.getClassLoader();
        }

        @Override
        public void setPermissions(Permissions permissions) {
        }

        @Override
        public void setNoPermissions() {
        }

        @Override
        public void setParentClassLoader(ClassLoader optionalParentClassLoader) {
        }

        @Override
        public void setDebuggingInformation(boolean debugSource, boolean debugLines, boolean debugVars) {
        }

        @Override
        public void cook(String optionalFileName, Reader r) {
            cooked = true;
        }

        @Override
        public void cook(Reader r) {
            cooked = true;
        }

        @Override
        public void cook(InputStream is) {
            cooked = true;
        }

        @Override
        public void cook(String optionalFileName, InputStream is) {
            cooked = true;
        }

        @Override
        public void cook(InputStream is, String optionalEncoding) {
            cooked = true;
        }

        @Override
        public void cook(String optionalFileName, InputStream is, String optionalEncoding) {
            cooked = true;
        }

        @Override
        public void cook(String s) throws CompileException {
            if (!s.contains("main")) {
                throw new CompileException("Class body must declare a main method", null);
            }
            cooked = true;
        }

        @Override
        public void cook(String optionalFileName, String s) throws CompileException {
            cook(s);
        }

        @Override
        public void cookFile(File file) {
            cooked = true;
        }

        @Override
        public void cookFile(File file, String optionalEncoding) {
            cooked = true;
        }

        @Override
        public void cookFile(String fileName) {
            cooked = true;
        }

        @Override
        public void cookFile(String fileName, String optionalEncoding) {
            cooked = true;
        }

        @Override
        public void setCompileErrorHandler(ErrorHandler optionalCompileErrorHandler) {
        }

        @Override
        public void setWarningHandler(WarningHandler optionalWarningHandler) {
        }

        private UnsupportedOperationException unsupported(String operation) {
            return new UnsupportedOperationException(operation);
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private ResourceBackedClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!PROPERTIES_RESOURCE_NAME.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(FACTORY_CONFIGURATION);
        }
    }
}
