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

import org.apache.commons.httpclient.methods.HeadMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HeadMethodTest {
    private static final String HEAD_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.HeadMethod";

    @Test
    void compilerGeneratedClassLookupResolvesHeadMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HeadMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HeadMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String headMethodClassName = new StringBuilder("org.apache.commons.httpclient.methods")
                .append(".HeadMethod")
                .toString();

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(headMethodClassName);

        assertThat(resolvedClass).isEqualTo(HeadMethod.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HeadMethodClassLoader classLoader = newHeadMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    HEAD_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(HEAD_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(HeadMethod.class);
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
    void noArgConstructorInitializesHeadMethodDefaults() {
        HeadMethod method = new HeadMethod();

        assertThat(method.getName()).isEqualTo("HEAD");
        assertThat(method.getFollowRedirects()).isTrue();
    }

    @Test
    void uriConstructorKeepsUriStateAndEnablesRedirects() throws Exception {
        HeadMethod method = new HeadMethod("http://example.com/status?check=links");

        assertThat(method.getName()).isEqualTo("HEAD");
        assertThat(method.getFollowRedirects()).isTrue();
        assertThat(method.getPath()).isEqualTo("/status");
        assertThat(method.getQueryString()).isEqualTo("check=links");
        assertThat(method.getURI().toString()).isEqualTo("http://example.com/status?check=links");
    }

    @Test
    @SuppressWarnings("deprecation")
    void recycleRestoresHeadMethodRedirectDefault() {
        HeadMethod method = new HeadMethod("/resource");
        method.setFollowRedirects(false);

        method.recycle();

        assertThat(method.getName()).isEqualTo("HEAD");
        assertThat(method.getFollowRedirects()).isTrue();
        assertThat(method.getPath()).isEqualTo("/");
    }

    private static HeadMethodClassLoader newHeadMethodClassLoader() {
        URL location = HeadMethod.class.getProtectionDomain().getCodeSource().getLocation();
        return new HeadMethodClassLoader(
                new URL[] {location},
                HeadMethodTest.class.getClassLoader());
    }

    private static final class HeadMethodClassLoader extends URLClassLoader {
        private HeadMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HEAD_METHOD_CLASS_NAME.equals(name)) {
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
