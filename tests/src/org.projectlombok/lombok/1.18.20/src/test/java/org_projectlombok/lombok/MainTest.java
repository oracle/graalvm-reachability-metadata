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

    private static final String LOMBOK_LAUNCH_MAIN = "lombok.launch.Main";

    @Test
    void launchMainDelegatesToCoreHelpCommand() throws Throwable {
        final PrintStream originalOutput = System.out;
        final ClassLoader originalContextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(printStream);

            invokeLaunchMain("--help");

            assertThat(output.toString(StandardCharsets.UTF_8))
                    .startsWith("projectlombok.org v")
                    .contains("Run 'lombok license' to see the lombok license agreement.");
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (!(cause instanceof Error error
                    && NativeImageSupport.isUnsupportedFeatureError(error))) {
                throw cause;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            System.setOut(originalOutput);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void invokeLaunchMain(String... arguments) throws Exception {
        final Class<?> mainType = Class.forName(LOMBOK_LAUNCH_MAIN);
        final Method main = mainType.getDeclaredMethod("main", String[].class);
        main.setAccessible(true);
        main.invoke(null, new Object[] {arguments});
    }
}
