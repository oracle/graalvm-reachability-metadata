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
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StringPartTest {
    private static final String STRING_PART_CLASS_NAME =
            "org.apache.commons.httpclient.methods.multipart.StringPart";
    private static final String STRING_PART_RESOURCE =
            "org/apache/commons/httpclient/methods/multipart/StringPart.class";

    @Test
    @Order(1)
    void instrumentableCopyInitializesThroughLegacyClassHelper() throws Exception {
        try (StringPartClassLoader classLoader = newInstrumentableStringPartClassLoader()) {
            Class<?> partClass = Class.forName(
                    STRING_PART_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(partClass.getName()).isEqualTo(STRING_PART_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(2)
    void classInitializationUsesLegacyClassLiteralHelper() throws Exception {
        Class<?> partClass = Class.forName(
                STRING_PART_CLASS_NAME,
                true,
                StringPartTest.class.getClassLoader());

        assertThat(partClass).isSameAs(StringPart.class);
    }

    @Test
    @Order(3)
    void legacyClassLiteralHelperLoadsStringPartType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                StringPart.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                StringPart.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedStringPartClass = (Class<?>) classLookup.invoke(STRING_PART_CLASS_NAME);
        Class<?> resolvedStringClass = (Class<?>) classLookup.invoke("java.lang.String");

        assertThat(resolvedStringPartClass).isSameAs(StringPart.class);
        assertThat(resolvedStringClass).isSameAs(String.class);
    }

    @Test
    @Order(4)
    void legacyClassLiteralHelperWrapsMissingClassFailures() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                StringPart.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                StringPart.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        assertThatThrownBy(() -> classLookup.invoke("example.missing.StringPart"))
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessageContaining("example.missing.StringPart");
    }

    @Test
    @Order(5)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (StringPartClassLoader classLoader = newStringPartClassLoader()) {
            Class<?> partClass = Class.forName(
                    STRING_PART_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(partClass.getName()).isEqualTo(STRING_PART_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(6)
    void sendsRepeatableTextPartAndReencodesAfterCharsetChange() throws Exception {
        String value = "caf\u00e9";
        StringPart part = new StringPart("message", value, StandardCharsets.UTF_8.name());

        byte[] utf8Multipart = send(part);
        String utf8Text = new String(utf8Multipart, StandardCharsets.ISO_8859_1);

        assertThat(part.getName()).isEqualTo("message");
        assertThat(part.getContentType()).isEqualTo(StringPart.DEFAULT_CONTENT_TYPE);
        assertThat(part.getCharSet()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(part.getTransferEncoding()).isEqualTo(StringPart.DEFAULT_TRANSFER_ENCODING);
        assertThat(part.isRepeatable()).isTrue();
        assertThat(part.length()).isEqualTo(utf8Multipart.length);
        assertThat(part.toString()).isEqualTo("message");
        assertThat(utf8Text)
                .contains("Content-Disposition: form-data; name=\"message\"")
                .contains("Content-Type: text/plain; charset=UTF-8")
                .contains("Content-Transfer-Encoding: 8bit");
        assertThat(extractBody(utf8Multipart))
                .isEqualTo(value.getBytes(StandardCharsets.UTF_8));

        part.setCharSet(StandardCharsets.ISO_8859_1.name());
        byte[] isoMultipart = send(part);

        assertThat(part.getCharSet()).isEqualTo(StandardCharsets.ISO_8859_1.name());
        assertThat(new String(isoMultipart, StandardCharsets.ISO_8859_1))
                .contains("Content-Type: text/plain; charset=ISO-8859-1");
        assertThat(extractBody(isoMultipart))
                .isEqualTo(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    @Order(7)
    void rejectsNullAndNulContainingValues() {
        assertThatThrownBy(() -> new StringPart("name", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value may not be null");
        assertThatThrownBy(() -> new StringPart("name", "contains\u0000nul"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NULs may not be present");
    }

    private static StringPartClassLoader newInstrumentableStringPartClassLoader()
            throws IOException {
        URL location = StringPart.class.getProtectionDomain().getCodeSource().getLocation();
        return new StringPartClassLoader(
                new URL[] {location},
                StringPartTest.class.getClassLoader(),
                stringPartClassBytesWithVisibleClassHelperLine());
    }

    private static StringPartClassLoader newStringPartClassLoader() {
        URL location = StringPart.class.getProtectionDomain().getCodeSource().getLocation();
        return new StringPartClassLoader(
                new URL[] {location},
                StringPartTest.class.getClassLoader(),
                null);
    }

    private static byte[] stringPartClassBytesWithVisibleClassHelperLine() throws IOException {
        ClassLoader classLoader = StringPart.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(STRING_PART_RESOURCE)) {
            if (input == null) {
                return null;
            }
            byte[] classBytes = input.readAllBytes();
            replaceSyntheticAttributeName(classBytes);
            return classBytes;
        }
    }

    private static void replaceSyntheticAttributeName(byte[] classBytes) {
        int constantPoolCount = unsignedShort(classBytes, 8);
        int offset = 10;
        byte[] synthetic = "Synthetic".getBytes(StandardCharsets.ISO_8859_1);
        byte[] replacement = "Synthetix".getBytes(StandardCharsets.ISO_8859_1);
        for (int index = 1; index < constantPoolCount; index++) {
            int tag = classBytes[offset++] & 0xff;
            switch (tag) {
                case 1:
                    int length = unsignedShort(classBytes, offset);
                    offset += 2;
                    if (length == synthetic.length
                            && startsWith(classBytes, synthetic, offset)) {
                        System.arraycopy(replacement, 0, classBytes, offset, replacement.length);
                    }
                    offset += length;
                    break;
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 18:
                    offset += 4;
                    break;
                case 5:
                case 6:
                    offset += 8;
                    constantPoolCount--;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    offset += 2;
                    break;
                case 15:
                    offset += 3;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
            }
        }
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static byte[] send(StringPart part) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        part.send(output);
        return output.toByteArray();
    }

    private static byte[] extractBody(byte[] multipart) {
        byte[] headerTerminator = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int bodyStart = indexOf(multipart, headerTerminator) + headerTerminator.length;
        int bodyEnd = multipart.length - "\r\n".getBytes(StandardCharsets.ISO_8859_1).length;
        byte[] body = new byte[bodyEnd - bodyStart];
        System.arraycopy(multipart, bodyStart, body, 0, body.length);
        return body;
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int sourceIndex = 0; sourceIndex <= source.length - target.length; sourceIndex++) {
            if (startsWith(source, target, sourceIndex)) {
                return sourceIndex;
            }
        }
        throw new IllegalArgumentException("Target bytes were not found");
    }

    private static boolean startsWith(byte[] source, byte[] target, int sourceIndex) {
        for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
            if (source[sourceIndex + targetIndex] != target[targetIndex]) {
                return false;
            }
        }
        return true;
    }

    private static final class StringPartClassLoader extends URLClassLoader {
        private final byte[] stringPartClassBytes;

        private StringPartClassLoader(
                URL[] urls,
                ClassLoader parent,
                byte[] stringPartClassBytes) {
            super(urls, parent);
            this.stringPartClassBytes = stringPartClassBytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (STRING_PART_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = loadStringPartClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        private Class<?> loadStringPartClass(String name) throws ClassNotFoundException {
            if (stringPartClassBytes == null) {
                return findClass(name);
            }
            return defineClass(name, stringPartClassBytes, 0, stringPartClassBytes.length);
        }
    }
}
