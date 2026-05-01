/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;

import net_sourceforge_htmlunit.htmlunit_core_js.shellscenarios.ShellConsoleScenario;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ShellConsoleTest {
    private static final String SCENARIO_PROVIDER =
            "net_sourceforge_htmlunit.htmlunit_core_js.shellscenarios.ShellConsoleScenarioRunner";

    @Test
    void createsJLineVersionOneConsoleThroughPublicFactory() throws Exception {
        try {
            String result = runScenario(Set.of("jline.console."));

            assertThat(result).isEqualTo("v1|v1-line|v1-prompted|v1-stream\n");
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @Test
    void createsJLineVersionTwoConsoleThroughPublicFactory() throws Exception {
        try {
            String result = runScenario(Set.of("jline.ConsoleReader", "jline.Completor"));

            assertThat(result).isEqualTo("v2|v2-line|v2-prompted|v2-stream\n");
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static String runScenario(Set<String> hiddenClassPrefixes) throws Exception {
        Set<URL> urls = new LinkedHashSet<>();
        addClassPathRoot(urls, ShellConsoleScenario.class);
        addClassPathRoot(urls, SCENARIO_PROVIDER.replace('.', '/') + ".class");
        addClassPathRoot(urls, "jline/ConsoleReader.class");
        addClassPathRoot(urls, "jline/console/ConsoleReader.class");
        addClassPathRoot(
                urls,
                "net/sourceforge/htmlunit/corejs/javascript/tools/shell/ShellConsole.class");

        try (IsolatedLibraryClassLoader classLoader =
                new IsolatedLibraryClassLoader(
                        urls.toArray(URL[]::new),
                        ShellConsoleTest.class.getClassLoader(),
                        hiddenClassPrefixes)) {
            ServiceLoader<ShellConsoleScenario> scenarios =
                    ServiceLoader.load(ShellConsoleScenario.class, classLoader);
            for (ShellConsoleScenario scenario : scenarios) {
                return scenario.run();
            }
        }
        throw new AssertionError("ShellConsole scenario provider was not found");
    }

    private static void addClassPathRoot(Set<URL> urls, Class<?> type) {
        addClassPathRoot(urls, type.getName().replace('.', '/') + ".class");
    }

    private static void addClassPathRoot(Set<URL> urls, String resourceName) {
        URL resource = ShellConsoleTest.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new AssertionError("Class resource was not found: " + resourceName);
        }
        urls.add(toClassPathRoot(resource, resourceName));
    }

    private static URL toClassPathRoot(URL resource, String resourceName) {
        try {
            String externalForm = resource.toExternalForm();
            if (externalForm.startsWith("jar:")) {
                return new URL(externalForm.substring(4, externalForm.indexOf("!/")));
            }
            String root = externalForm.substring(0, externalForm.length() - resourceName.length());
            return new URL(root);
        } catch (Exception exception) {
            throw new AssertionError("Unable to resolve class path root for " + resource, exception);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                return;
            }
            cause = cause.getCause();
        }
        throw error;
    }

    private static final class IsolatedLibraryClassLoader extends URLClassLoader {
        private final Set<String> hiddenClassPrefixes;
        private final Set<String> childFirstPrefixes;

        private IsolatedLibraryClassLoader(
                URL[] urls, ClassLoader parent, Set<String> hiddenClassPrefixes) {
            super(urls, parent);
            this.hiddenClassPrefixes = hiddenClassPrefixes;
            this.childFirstPrefixes = Set.of(
                    "net.sourceforge.htmlunit.",
                    "jline.",
                    "net_sourceforge_htmlunit.htmlunit_core_js.shellscenarios.ShellConsoleScenarioRunner");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String hiddenPrefix : hiddenClassPrefixes) {
                if (name.startsWith(hiddenPrefix)) {
                    throw new ClassNotFoundException(name);
                }
            }
            if (isChildFirstClass(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        private boolean isChildFirstClass(String name) {
            for (String childFirstPrefix : childFirstPrefixes) {
                if (name.startsWith(childFirstPrefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
