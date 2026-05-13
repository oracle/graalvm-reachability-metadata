/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FilePartTest {
    private static final String FILE_PART_CLASS_NAME =
            "org.apache.commons.httpclient.methods.multipart.FilePart";
    private static final String FILE_PART_LOOKUP_TARGET_PROPERTY =
            "commons.httpclient.filePart.classLookupTarget";

    @Test
    void legacyClassLiteralHelperLoadsMultipartPartSourceType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                FilePart.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                FilePart.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        String targetClassName = System.getProperty(
                FILE_PART_LOOKUP_TARGET_PROPERTY,
                PartSource.class.getName());

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(targetClassName);

        assertThat(resolvedClass.getName()).isEqualTo(targetClassName);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (FilePartClassLoader classLoader = newFilePartClassLoader()) {
            Class<?> filePartClass = Class.forName(
                    FILE_PART_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(filePartClass.getName()).isEqualTo(FILE_PART_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void sendsNamedFilePartWithDefaultHeadersAndRepeatableBody() throws Exception {
        byte[] body = "file part body".getBytes(StandardCharsets.ISO_8859_1);
        FilePart part = new FilePart("payload", new InMemoryPartSource("message.txt", body));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        part.send(output);
        String multipart = output.toString(StandardCharsets.ISO_8859_1);

        assertThat(part.getName()).isEqualTo("payload");
        assertThat(part.getContentType()).isEqualTo(FilePart.DEFAULT_CONTENT_TYPE);
        assertThat(part.getCharSet()).isEqualTo(FilePart.DEFAULT_CHARSET);
        assertThat(part.getTransferEncoding()).isEqualTo(FilePart.DEFAULT_TRANSFER_ENCODING);
        assertThat(part.isRepeatable()).isTrue();
        assertThat(part.length()).isEqualTo(output.size());
        assertThat(part.toString()).isEqualTo("payload");
        assertThat(multipart)
                .contains("----------------314159265358979323846")
                .contains("Content-Disposition: form-data; name=\"payload\"; "
                        + "filename=\"message.txt\"")
                .contains("Content-Type: application/octet-stream; charset=ISO-8859-1")
                .contains("Content-Transfer-Encoding: binary")
                .contains("file part body");
    }

    private static FilePartClassLoader newFilePartClassLoader() {
        URL location = FilePart.class.getProtectionDomain().getCodeSource().getLocation();
        return new FilePartClassLoader(
                new URL[] {location},
                FilePartTest.class.getClassLoader());
    }

    private static final class FilePartClassLoader extends URLClassLoader {
        private FilePartClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (FILE_PART_CLASS_NAME.equals(name)) {
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

    private static final class InMemoryPartSource implements PartSource {
        private final String fileName;
        private final byte[] body;

        private InMemoryPartSource(String fileName, byte[] body) {
            this.fileName = fileName;
            this.body = body.clone();
        }

        @Override
        public long getLength() {
            return body.length;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public InputStream createInputStream() {
            return new ByteArrayInputStream(body);
        }
    }
}
