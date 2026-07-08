/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MainTest {
    @Test
    void launcherDelegatesToLombokCoreMainVersionCommand() throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            if (!invokeLauncherMain("--version")) {
                return;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            System.setOut(previousOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .startsWith("v")
                .contains("\"")
                .endsWith(System.lineSeparator());
    }

    private static boolean invokeLauncherMain(String... args) throws Throwable {
        try {
            Class<?> launcherClass = Class.forName("lombok.launch.Main");
            Method main = launcherClass.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, new Object[] {args});
            return true;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return false;
            }
            throw cause;
        } catch (Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return false;
            }
            throw error;
        }
    }
}
