/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractJavaSourceClassLoaderTest {
    private static final String ABSTRACT_JAVA_SOURCE_CLASS_LOADER =
            "org.codehaus.commons.compiler.AbstractJavaSourceClassLoader";
    private static final String INVOCATION_PROPERTY =
            "org_codehaus_janino.commons_compiler.AbstractJavaSourceClassLoaderTest.invocation";

    @TempDir
    Path sourceDirectory;

    @Test
    public void commandLineEntryPointLoadsSourceClassAndInvokesItsMainMethod() throws Exception {
        Path packageDirectory = this.sourceDirectory.resolve("example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("GeneratedMain.java"), """
                package example;

                public class GeneratedMain {
                    public static void main(String[] args) {
                        System.setProperty(
                            "%s",
                            args[0] + ":" + args[1] + ":" + args.length
                        );
                    }
                }
                """.formatted(INVOCATION_PROPERTY), StandardCharsets.UTF_8);

        System.clearProperty(INVOCATION_PROPERTY);
        try {
            invokeJavaSourceClassLoaderMain(
                    "-sourcepath", this.sourceDirectory.toString(),
                    "-encoding", StandardCharsets.UTF_8.name(),
                    "-g",
                    "example.GeneratedMain",
                    "alpha",
                    "beta");
            assertThat(System.getProperty(INVOCATION_PROPERTY)).isEqualTo("alpha:beta:2");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (!isUnsupportedFeatureError(cause)
                    && !isUnsupportedNativeImageGeneratedClassFailure(
                            cause, "example.GeneratedMain")) {
                if (cause instanceof Error error) {
                    throw error;
                }
                throw exception;
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        } finally {
            System.clearProperty(INVOCATION_PROPERTY);
        }
    }

    private static void invokeJavaSourceClassLoaderMain(String... args) throws Exception {
        Class<?> javaSourceClassLoaderClass = Class.forName(ABSTRACT_JAVA_SOURCE_CLASS_LOADER);
        Method mainMethod = javaSourceClassLoaderClass.getDeclaredMethod("main", String[].class);
        mainMethod.setAccessible(true);
        mainMethod.invoke(null, (Object) args);
    }

    private static boolean isUnsupportedFeatureError(Throwable throwable) {
        return throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error);
    }

    private static boolean isUnsupportedNativeImageGeneratedClassFailure(
            Throwable throwable, String className) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException && className.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
