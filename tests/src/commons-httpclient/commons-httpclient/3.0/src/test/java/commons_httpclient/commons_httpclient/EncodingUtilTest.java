/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class EncodingUtilTest {
    private static final String ENCODING_UTIL_CLASS_NAME =
            "org.apache.commons.httpclient.util.EncodingUtil";

    @Test
    @Order(1)
    void compilerGeneratedClassLookupReturnsEncodingUtilClass() throws Exception {
        Method classLookup = EncodingUtil.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, ENCODING_UTIL_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(EncodingUtil.class);
    }

    @Test
    @Order(2)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (EncodingUtilClassLoader classLoader = newEncodingUtilClassLoader()) {
            Class<?> encodingUtilClass = Class.forName(
                    ENCODING_UTIL_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(encodingUtilClass.getName()).isEqualTo(ENCODING_UTIL_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(encodingUtilClass).isSameAs(EncodingUtil.class);
            } else {
                assertThat(encodingUtilClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(3)
    void formUrlEncodeEscapesNamesAndValuesWithRequestedCharset() {
        NameValuePair[] pairs = new NameValuePair[] {
                new NameValuePair("city name", "z\u00FCrich & lake"),
                new NameValuePair(null, "ignored"),
                new NameValuePair("empty", null)
        };

        String encoded = EncodingUtil.formUrlEncode(pairs, "UTF-8");

        assertThat(encoded).isEqualToIgnoringCase("city+name=z%C3%BCrich+%26+lake&empty=");
    }

    @Test
    @Order(4)
    void formUrlEncodeFallsBackToIso88591WhenCharsetIsUnsupported() {
        NameValuePair[] pairs = new NameValuePair[] {
                new NameValuePair("accent", "\u00E9")
        };

        String encoded = EncodingUtil.formUrlEncode(pairs, "not-a-real-charset");

        assertThat(encoded).isEqualToIgnoringCase("accent=%E9");
    }

    @Test
    @Order(5)
    void byteAndStringHelpersUseRequestedCharsetAndFallbackForUnsupportedCharset() {
        byte[] utf8Bytes = "caf\u00E9".getBytes(StandardCharsets.UTF_8);

        assertThat(EncodingUtil.getBytes("caf\u00E9", "UTF-8")).containsExactly(utf8Bytes);
        assertThat(EncodingUtil.getString(utf8Bytes, "UTF-8")).isEqualTo("caf\u00E9");
        assertThat(EncodingUtil.getString(utf8Bytes, 3, 2, "UTF-8")).isEqualTo("\u00E9");
        assertThat(EncodingUtil.getBytes("plain", "not-a-real-charset")).containsExactly("plain".getBytes());
        assertThat(EncodingUtil.getString("plain".getBytes(), "not-a-real-charset")).isEqualTo("plain");
    }

    @Test
    @Order(6)
    void asciiHelpersRoundTripAndRejectNullInput() {
        byte[] bytes = EncodingUtil.getAsciiBytes("GET / HTTP/1.1");

        assertThat(bytes).containsExactly(
                (byte) 'G',
                (byte) 'E',
                (byte) 'T',
                (byte) ' ',
                (byte) '/',
                (byte) ' ',
                (byte) 'H',
                (byte) 'T',
                (byte) 'T',
                (byte) 'P',
                (byte) '/',
                (byte) '1',
                (byte) '.',
                (byte) '1');
        assertThat(EncodingUtil.getAsciiString(bytes)).isEqualTo("GET / HTTP/1.1");
        assertThat(EncodingUtil.getAsciiString(bytes, 6, 8)).isEqualTo("HTTP/1.1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getAsciiBytes(null))
                .withMessage("Parameter may not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getAsciiString(null, 0, 0))
                .withMessage("Parameter may not be null");
    }

    @Test
    @Order(7)
    void byteAndStringHelpersRejectNullOrEmptyRequiredParameters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getBytes(null, "UTF-8"))
                .withMessage("data may not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getBytes("data", ""))
                .withMessage("charset may not be null or empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getString(null, 0, 0, "UTF-8"))
                .withMessage("Parameter may not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncodingUtil.getString(new byte[] {(byte) 'x'}, null))
                .withMessage("charset may not be null or empty");
    }

    private static EncodingUtilClassLoader newEncodingUtilClassLoader() {
        URL location = EncodingUtil.class.getProtectionDomain().getCodeSource().getLocation();
        return new EncodingUtilClassLoader(new URL[] {location}, EncodingUtilTest.class.getClassLoader());
    }

    private static final class EncodingUtilClassLoader extends URLClassLoader {
        private EncodingUtilClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (ENCODING_UTIL_CLASS_NAME.equals(name)) {
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
