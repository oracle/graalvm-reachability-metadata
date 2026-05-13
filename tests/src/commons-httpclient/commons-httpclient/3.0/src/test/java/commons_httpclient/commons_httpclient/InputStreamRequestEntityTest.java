/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class InputStreamRequestEntityTest {
    private static final String INPUT_STREAM_REQUEST_ENTITY_CLASS_NAME =
            "org.apache.commons.httpclient.methods.InputStreamRequestEntity";

    @Test
    void legacyClassLiteralHelperLoadsInputStreamRequestEntityType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                InputStreamRequestEntity.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                InputStreamRequestEntity.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(
                INPUT_STREAM_REQUEST_ENTITY_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(InputStreamRequestEntity.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (InputStreamRequestEntityClassLoader classLoader =
                newInputStreamRequestEntityClassLoader()) {
            Class<?> entityClass = Class.forName(
                    INPUT_STREAM_REQUEST_ENTITY_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(entityClass.getName()).isEqualTo(INPUT_STREAM_REQUEST_ENTITY_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(entityClass).isSameAs(InputStreamRequestEntity.class);
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
    void buffersAutomaticallySizedContentAndWritesItRepeatably() throws Exception {
        byte[] body = "buffered request body".getBytes(StandardCharsets.ISO_8859_1);
        InputStreamRequestEntity entity = new InputStreamRequestEntity(
                new ByteArrayInputStream(body),
                "text/plain");

        assertThat(entity.getContentType()).isEqualTo("text/plain");
        assertThat(entity.isRepeatable()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(body.length);
        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.getContent()).isNull();

        ByteArrayOutputStream firstWrite = new ByteArrayOutputStream();
        ByteArrayOutputStream secondWrite = new ByteArrayOutputStream();
        entity.writeRequest(firstWrite);
        entity.writeRequest(secondWrite);

        assertThat(firstWrite.toByteArray()).isEqualTo(body);
        assertThat(secondWrite.toByteArray()).isEqualTo(body);
    }

    @Test
    void writesKnownLengthContentDirectlyFromInputStream() throws Exception {
        byte[] body = "streamed request body".getBytes(StandardCharsets.ISO_8859_1);
        InputStreamRequestEntity entity = new InputStreamRequestEntity(
                new ByteArrayInputStream(body),
                body.length,
                "application/octet-stream");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeRequest(output);

        assertThat(entity.getContentLength()).isEqualTo(body.length);
        assertThat(entity.getContentType()).isEqualTo("application/octet-stream");
        assertThat(entity.isRepeatable()).isFalse();
        assertThat(output.toByteArray()).isEqualTo(body);
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> new InputStreamRequestEntity(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content cannot be null");
    }

    private static InputStreamRequestEntityClassLoader newInputStreamRequestEntityClassLoader() {
        URL location = InputStreamRequestEntity.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new InputStreamRequestEntityClassLoader(
                new URL[] {location},
                InputStreamRequestEntityTest.class.getClassLoader());
    }

    private static final class InputStreamRequestEntityClassLoader extends URLClassLoader {
        private InputStreamRequestEntityClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (INPUT_STREAM_REQUEST_ENTITY_CLASS_NAME.equals(name)) {
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
