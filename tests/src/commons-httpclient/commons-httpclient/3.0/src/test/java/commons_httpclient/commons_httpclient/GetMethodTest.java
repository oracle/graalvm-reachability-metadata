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

import org.apache.commons.httpclient.methods.GetMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class GetMethodTest {
    private static final String GET_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.GetMethod";

    @Test
    void compilerGeneratedClassLookupResolvesGetMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                GetMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                GetMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String getMethodClassName = new StringBuilder("org.apache.commons.httpclient.methods")
                .append(".GetMethod")
                .toString();

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(getMethodClassName);

        assertThat(resolvedClass).isEqualTo(GetMethod.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (GetMethodClassLoader classLoader = newGetMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    GET_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(GET_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(GetMethod.class);
            } else {
                assertThat(methodClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void noArgConstructorInitializesGetMethodDefaults() {
        GetMethod method = new GetMethod();

        assertThat(method.getName()).isEqualTo("GET");
        assertThat(method.getFollowRedirects()).isTrue();
    }

    @Test
    void uriConstructorKeepsUriStateAndEnablesRedirects() throws Exception {
        GetMethod method = new GetMethod("http://example.com/search?q=graalvm");

        assertThat(method.getName()).isEqualTo("GET");
        assertThat(method.getFollowRedirects()).isTrue();
        assertThat(method.getPath()).isEqualTo("/search");
        assertThat(method.getQueryString()).isEqualTo("q=graalvm");
        assertThat(method.getURI().toString()).isEqualTo("http://example.com/search?q=graalvm");
    }

    @Test
    @SuppressWarnings("deprecation")
    void recycleRestoresGetMethodRedirectDefault() {
        GetMethod method = new GetMethod("/resource");
        method.setFollowRedirects(false);

        method.recycle();

        assertThat(method.getName()).isEqualTo("GET");
        assertThat(method.getFollowRedirects()).isTrue();
        assertThat(method.getPath()).isEqualTo("/");
    }

    private static GetMethodClassLoader newGetMethodClassLoader() {
        URL location = GetMethod.class.getProtectionDomain().getCodeSource().getLocation();
        return new GetMethodClassLoader(
                new URL[] {location},
                GetMethodTest.class.getClassLoader());
    }

    private static final class GetMethodClassLoader extends URLClassLoader {
        private GetMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (GET_METHOD_CLASS_NAME.equals(name)) {
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
