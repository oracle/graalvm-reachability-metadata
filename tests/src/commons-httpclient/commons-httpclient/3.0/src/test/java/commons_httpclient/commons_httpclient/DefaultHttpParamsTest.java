/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.params.DefaultHttpParams;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DefaultHttpParamsTest {
    private static final String DEFAULT_HTTP_PARAMS_CLASS_NAME =
            "org.apache.commons.httpclient.params.DefaultHttpParams";

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (DefaultHttpParamsClassLoader classLoader = newDefaultHttpParamsClassLoader()) {
            Class<?> paramsClass = Class.forName(
                    DEFAULT_HTTP_PARAMS_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(paramsClass.getName()).isEqualTo(DEFAULT_HTTP_PARAMS_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(paramsClass).isSameAs(DefaultHttpParams.class);
            } else {
                assertThat(paramsClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void constructorInitializesDefaultsAndParameterLookup() {
        DefaultHttpParams defaults = new DefaultHttpParams(null);
        defaults.setParameter("example.parameter", "default-value");
        DefaultHttpParams params = new DefaultHttpParams(defaults);

        assertThat(params.getDefaults()).isSameAs(defaults);
        assertThat(params.getParameter("example.parameter")).isEqualTo("default-value");
        assertThat(params.isParameterSet("example.parameter")).isTrue();
        assertThat(params.isParameterSetLocally("example.parameter")).isFalse();
    }

    private static DefaultHttpParamsClassLoader newDefaultHttpParamsClassLoader() {
        URL location = DefaultHttpParams.class.getProtectionDomain()
                .getCodeSource().getLocation();
        return new DefaultHttpParamsClassLoader(new URL[] {location},
                DefaultHttpParamsTest.class.getClassLoader());
    }

    private static final class DefaultHttpParamsClassLoader extends URLClassLoader {
        private DefaultHttpParamsClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (DEFAULT_HTTP_PARAMS_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
