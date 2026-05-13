/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class EntityEnclosingMethodTest {
    private static final String ENTITY_ENCLOSING_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.EntityEnclosingMethod";

    @Test
    void legacyClassLiteralHelperLoadsEntityEnclosingMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                EntityEnclosingMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                EntityEnclosingMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(
                ENTITY_ENCLOSING_METHOD_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(EntityEnclosingMethod.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (EntityEnclosingMethodClassLoader classLoader = newEntityEnclosingMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    ENTITY_ENCLOSING_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(ENTITY_ENCLOSING_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(EntityEnclosingMethod.class);
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
    void putMethodInitializesEntityEnclosingMethodAndWritesStringRequestBody() throws Exception {
        PutMethod method = new PutMethod("/documents/1");

        assertThat(method).isInstanceOf(EntityEnclosingMethod.class);
        assertThat(method.getFollowRedirects()).isFalse();
        assertThatThrownBy(() -> method.setFollowRedirects(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be redirected");

        method.setRequestBody("payload");
        RequestEntity requestEntity = method.getRequestEntity();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        requestEntity.writeRequest(output);

        assertThat(requestEntity.getContentLength()).isEqualTo("payload".length());
        assertThat(new String(output.toByteArray(), StandardCharsets.ISO_8859_1))
                .isEqualTo("payload");
    }

    private static EntityEnclosingMethodClassLoader newEntityEnclosingMethodClassLoader() {
        URL location = EntityEnclosingMethod.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new EntityEnclosingMethodClassLoader(
                new URL[] {location},
                EntityEnclosingMethodTest.class.getClassLoader());
    }

    private static final class EntityEnclosingMethodClassLoader extends URLClassLoader {
        private EntityEnclosingMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (ENTITY_ENCLOSING_METHOD_CLASS_NAME.equals(name)) {
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
