/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.integration.commandline.LiquibaseLauncher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibaseLauncherTest {

    private static final String COMMAND_LINE_CLASS_NAME = "liquibase.integration.commandline.LiquibaseCommandLine";
    private static final String LIQUIBASE_HOME_PROPERTY = "liquibase.home";
    private static final String LAUNCHER_PARENT_CLASSLOADER_PROPERTY = "liquibase.launcher.parent_classloader";
    private static final String INCLUDE_SYSTEM_CLASSPATH_PROPERTY = "liquibase.includeSystemClasspath";
    private static final String CLASSPATH_PROPERTY = "liquibase.classpath";
    private static final String DEBUG_PROPERTY = "liquibase.launcher.debug";
    private static final String STUB_ARGS_LENGTH_PROPERTY = "liquibase.launcher.stub.args.length";
    private static final String STUB_ARG_ZERO_PROPERTY = "liquibase.launcher.stub.arg.0";
    private static final byte[] COMMAND_LINE_STUB_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAIAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAjbGlxdWliYXNlLmxh
            dW5jaGVyLnN0dWIuYXJncy5sZW5ndGgKAAoACwcADAwADQAOAQARamF2YS9sYW5nL0ludGVnZXIBAAh0b1N0cmluZwEAFShJKUxq
            YXZhL2xhbmcvU3RyaW5nOwoAEAARBwASDAATABQBABBqYXZhL2xhbmcvU3lzdGVtAQALc2V0UHJvcGVydHkBADgoTGphdmEvbGFu
            Zy9TdHJpbmc7TGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvU3RyaW5nOwgAFgEAHWxpcXVpYmFzZS5sYXVuY2hlci5zdHVi
            LmFyZy4wBwAYAQA2bGlxdWliYXNlL2ludGVncmF0aW9uL2NvbW1hbmRsaW5lL0xpcXVpYmFzZUNvbW1hbmRMaW5lAQAEQ29kZQEA
            D0xpbmVOdW1iZXJUYWJsZQEABG1haW4BABYoW0xqYXZhL2xhbmcvU3RyaW5nOylWAQANU3RhY2tNYXBUYWJsZQEAClNvdXJjZUZp
            bGUBABlMaXF1aWJhc2VDb21tYW5kTGluZS5qYXZhADEAFwACAAAAAAACAAEABQAGAAEAGQAAAB0AAQABAAAABSq3AAGxAAAAAQAa
            AAAABgABAAAAAwAJABsAHAABABkAAABHAAMAAQAAABoSByq+uAAJuAAPVyq+ngAMEhUqAzK4AA9XsQAAAAIAGgAAABIABAAAAAUA
            CwAGABAABwAZAAkAHQAAAAMAARkAAQAeAAAAAgAf
            """);

    @TempDir
    Path tempDir;

    @Test
    void mainLoadsCommandLineClassAndInvokesMainThroughLauncherClassLoader() throws Exception {
        Path liquibaseHome = tempDir.resolve("liquibase-home");
        Files.createDirectories(liquibaseHome.resolve("internal/lib"));
        Files.createDirectories(liquibaseHome.resolve("internal/extensions"));
        Files.createDirectories(liquibaseHome.resolve("lib"));

        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        Map<String, String> previousProperties = saveProperties(
                LIQUIBASE_HOME_PROPERTY,
                LAUNCHER_PARENT_CLASSLOADER_PROPERTY,
                INCLUDE_SYSTEM_CLASSPATH_PROPERTY,
                CLASSPATH_PROPERTY,
                DEBUG_PROPERTY,
                STUB_ARGS_LENGTH_PROPERTY,
                STUB_ARG_ZERO_PROPERTY
        );

        LauncherCommandLineClassLoader commandLineClassLoader = new LauncherCommandLineClassLoader();
        Thread.currentThread().setContextClassLoader(commandLineClassLoader);
        System.setProperty(LIQUIBASE_HOME_PROPERTY, liquibaseHome.toString());
        System.setProperty(LAUNCHER_PARENT_CLASSLOADER_PROPERTY, "thread");
        System.setProperty(INCLUDE_SYSTEM_CLASSPATH_PROPERTY, "true");
        System.clearProperty(CLASSPATH_PROPERTY);
        System.clearProperty(DEBUG_PROPERTY);
        System.clearProperty(STUB_ARGS_LENGTH_PROPERTY);
        System.clearProperty(STUB_ARG_ZERO_PROPERTY);

        String stubArgsLength;
        String stubArgZero;
        try {
            LiquibaseLauncher.main(new String[] {"--version"});
            stubArgsLength = System.getProperty(STUB_ARGS_LENGTH_PROPERTY);
            stubArgZero = System.getProperty(STUB_ARG_ZERO_PROPERTY);
        } catch (Throwable throwable) {
            if (isUnsupportedNativeImageLauncherFailure(throwable)) {
                throw new TestAbortedException(
                        "Native image runtime does not support resolving Liquibase CLI classes through the isolated launcher ClassLoader path",
                        throwable
                );
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError(throwable);
        } finally {
            closeLauncherClassLoader();
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperties(previousProperties);
        }

        assertThat(stubArgsLength).isEqualTo("1");
        assertThat(stubArgZero).isEqualTo("--version");
        assertThat(commandLineClassLoader.loadedCommandLine).isTrue();
    }

    private static Map<String, String> saveProperties(String... propertyNames) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String propertyName : propertyNames) {
            values.put(propertyName, System.getProperty(propertyName));
        }
        return values;
    }

    private static void restoreProperties(Map<String, String> propertyValues) {
        propertyValues.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    private static void closeLauncherClassLoader() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            urlClassLoader.close();
        }
    }

    private static boolean isUnsupportedNativeImageLauncherFailure(Throwable throwable) {
        if (!isNativeImageRuntime()) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("picocli/CommandLine$")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class LauncherCommandLineClassLoader extends ClassLoader {

        private boolean loadedCommandLine;

        private LauncherCommandLineClassLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (COMMAND_LINE_CLASS_NAME.equals(name)) {
                        loadedClass = defineClass(name, COMMAND_LINE_STUB_BYTES, 0, COMMAND_LINE_STUB_BYTES.length);
                        loadedCommandLine = true;
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
