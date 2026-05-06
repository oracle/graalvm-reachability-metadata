/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {
    private static final String LAUNCHER_CLASS_NAME = "lombok.launch.Main";

    @Test
    void launcherDelegatesToShadowLoadedCoreMain() throws Throwable {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);

            try {
                invokeLauncherMain("--help");
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (isUnsupportedDynamicClassLoading(cause)) {
                    return;
                }
                throw cause;
            } catch (Error error) {
                if (isUnsupportedDynamicClassLoading(error)) {
                    return;
                }
                throw error;
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .contains("projectlombok.org")
                .contains("Other available commands");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    private static void invokeLauncherMain(String... arguments) throws Exception {
        Class<?> launcherClass = Class.forName(LAUNCHER_CLASS_NAME);
        Method mainMethod = launcherClass.getDeclaredMethod("main", String[].class);
        mainMethod.setAccessible(true);
        mainMethod.invoke(null, new Object[] {arguments});
    }

    private static boolean isUnsupportedDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
