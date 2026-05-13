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

import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MultipartRequestEntityTest {
    private static final String BOUNDARY = "----------------314159265358979323846";
    private static final String MULTIPART_REQUEST_ENTITY_CLASS_NAME =
            "org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity";

    @Test
    void legacyClassLiteralHelperLoadsMultipartRequestEntityType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MultipartRequestEntity.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                MultipartRequestEntity.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(
                MULTIPART_REQUEST_ENTITY_CLASS_NAME);
        Class<?> resolvedPartClass = (Class<?>) classLookup.invoke(
                "org.apache.commons.httpclient.methods.multipart.StringPart");
        Class<?> resolvedJdkClass = (Class<?>) classLookup.invoke("java.lang.String");

        assertThat(resolvedClass).isSameAs(MultipartRequestEntity.class);
        assertThat(resolvedPartClass).isSameAs(StringPart.class);
        assertThat(resolvedJdkClass).isSameAs(String.class);
    }

    @Test
    void legacyClassLiteralHelperWrapsMissingClassFailures() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MultipartRequestEntity.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                MultipartRequestEntity.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        assertThatThrownBy(() -> classLookup.invoke("example.missing.MultipartRequestEntity"))
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessageContaining("example.missing.MultipartRequestEntity");
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (MultipartRequestEntityClassLoader classLoader =
                newMultipartRequestEntityClassLoader()) {
            Class<?> entityClass = Class.forName(
                    MULTIPART_REQUEST_ENTITY_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(entityClass.getName()).isEqualTo(MULTIPART_REQUEST_ENTITY_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(entityClass).isSameAs(MultipartRequestEntity.class);
            } else {
                assertThat(entityClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void writesRepeatableStringPartsWithConfiguredBoundary() throws Exception {
        HttpMethodParams params = new HttpMethodParams();
        params.setParameter(HttpMethodParams.MULTIPART_BOUNDARY, BOUNDARY);
        Part[] parts = {
                new StringPart("first", "alpha"),
                new StringPart("second", "beta")
        };

        MultipartRequestEntity entity = new MultipartRequestEntity(parts, params);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeRequest(output);
        String body = output.toString(StandardCharsets.ISO_8859_1);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.getContentType())
                .isEqualTo("multipart/form-data; boundary=" + BOUNDARY);
        assertThat(entity.getContentLength()).isEqualTo(output.size());
        assertThat(body)
                .contains("--" + BOUNDARY)
                .contains("Content-Disposition: form-data; name=\"first\"")
                .contains("Content-Type: text/plain; charset=US-ASCII")
                .contains("Content-Transfer-Encoding: 8bit")
                .contains("\r\n\r\nalpha\r\n")
                .contains("Content-Disposition: form-data; name=\"second\"")
                .contains("\r\n\r\nbeta\r\n")
                .endsWith("--" + BOUNDARY + "--\r\n");
    }

    @Test
    void rejectsMissingConstructorArguments() {
        HttpMethodParams params = new HttpMethodParams();
        Part[] parts = {new StringPart("name", "value")};

        assertThatThrownBy(() -> new MultipartRequestEntity(null, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parts cannot be null");
        assertThatThrownBy(() -> new MultipartRequestEntity(parts, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params cannot be null");
    }

    private static MultipartRequestEntityClassLoader newMultipartRequestEntityClassLoader() {
        URL location = MultipartRequestEntity.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new MultipartRequestEntityClassLoader(
                new URL[] {location},
                MultipartRequestEntityTest.class.getClassLoader());
    }

    private static final class MultipartRequestEntityClassLoader extends URLClassLoader {
        private MultipartRequestEntityClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (MULTIPART_REQUEST_ENTITY_CLASS_NAME.equals(name)) {
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
