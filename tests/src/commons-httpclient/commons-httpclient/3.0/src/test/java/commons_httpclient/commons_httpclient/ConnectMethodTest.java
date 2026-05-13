/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.ConnectMethod;
import org.apache.commons.httpclient.HttpMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ConnectMethodTest {
    private static final String CONNECT_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.ConnectMethod";

    @Test
    void compilerGeneratedClassLookupResolvesConnectMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ConnectMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                ConnectMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(CONNECT_METHOD_CLASS_NAME);

        assertThat(resolvedClass).isEqualTo(ConnectMethod.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (ConnectMethodClassLoader classLoader = newConnectMethodClassLoader()) {
            Class<?> connectMethodClass = Class.forName(
                    CONNECT_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(connectMethodClass.getName()).isEqualTo(CONNECT_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(connectMethodClass).isSameAs(ConnectMethod.class);
            } else {
                assertThat(connectMethodClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void constructorInitializesConnectMethodAndReportsMethodName() {
        ConnectMethod method = new ConnectMethod();

        assertThat(method.getName()).isEqualTo(ConnectMethod.NAME);
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedWrappingConstructorInitializesConnectMethod() {
        ConnectMethod wrappedMethod = new ConnectMethod(new ConnectMethod());

        assertThat(wrappedMethod).isInstanceOf(HttpMethod.class);
        assertThat(wrappedMethod.getName()).isEqualTo("CONNECT");
    }

    private static ConnectMethodClassLoader newConnectMethodClassLoader() {
        URL location = ConnectMethod.class.getProtectionDomain().getCodeSource().getLocation();
        return new ConnectMethodClassLoader(new URL[] {location},
                ConnectMethodTest.class.getClassLoader());
    }

    private static final class ConnectMethodClassLoader extends URLClassLoader {
        private ConnectMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (CONNECT_METHOD_CLASS_NAME.equals(name)) {
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
