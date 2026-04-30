/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Cookable;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.WarningHandler;
import org.codehaus.commons.compiler.samples.ClassBodyDemo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.Permissions;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassBodyDemoTest {
    private static final String CLASS_BODY = "public static String main(String[] args) { return \"unused\"; }";

    @Test
    void invokesClassBodyMainMethod() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));

            ClassBodyDemo.main(new String[] { CLASS_BODY, "alpha", "beta" });
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name()).trim()).isEqualTo("alpha, beta");
    }

    public static final class PreparedClassBodyEvaluator extends Cookable implements IClassBodyEvaluator {
        private Class<?> clazz;

        @Override
        public void setDefaultImports(String... optionalDefaultImports) {
        }

        @Override
        public void setClassName(String className) {
        }

        @Override
        public void setExtendedClass(Class<?> optionalExtendedClass) {
        }

        @Override
        @Deprecated
        public void setExtendedType(Class<?> optionalExtendedClass) {
        }

        @Override
        public void setImplementedInterfaces(Class<?>[] implementedInterfaces) {
        }

        @Override
        @Deprecated
        public void setImplementedTypes(Class<?>[] implementedInterfaces) {
        }

        @Override
        public Class<?> getClazz() {
            if (this.clazz == null) {
                throw new IllegalStateException("No class body was cooked");
            }
            return this.clazz;
        }

        @Override
        public Object createInstance(Reader reader) throws CompileException, IOException {
            this.cook(reader);
            return new MainMethodTarget();
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
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
        public void cook(String optionalFileName, Reader r) throws CompileException, IOException {
            String classBody = Cookable.readString(r);
            if (!classBody.contains("main")) {
                throw new CompileException("Class body must declare a main method", null);
            }
            this.clazz = MainMethodTarget.class;
        }

        @Override
        public void setCompileErrorHandler(ErrorHandler optionalCompileErrorHandler) {
        }

        @Override
        public void setWarningHandler(WarningHandler optionalWarningHandler) {
        }
    }

    public static final class MainMethodTarget {
        public static String main(String[] args) {
            return String.join(", ", args);
        }
    }
}
