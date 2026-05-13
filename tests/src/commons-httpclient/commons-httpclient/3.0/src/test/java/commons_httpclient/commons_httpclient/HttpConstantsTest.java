/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.HttpConstants;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpConstantsTest {
    private static final String HTTP_CONSTANTS_CLASS_NAME = "org.apache.commons.httpclient.HttpConstants";

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpConstantsClassLoader classLoader = newHttpConstantsClassLoader()) {
            Class<?> httpConstantsClass = Class.forName(
                    HTTP_CONSTANTS_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(httpConstantsClass.getName()).isEqualTo(HTTP_CONSTANTS_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(httpConstantsClass).isSameAs(HttpConstants.class);
            } else {
                assertThat(httpConstantsClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void elementEncodingRoundTripUsesHttpAsciiCharset() {
        byte[] bytes = HttpConstants.getBytes("Host: example.com");

        assertThat(bytes).containsExactly(
                (byte) 'H',
                (byte) 'o',
                (byte) 's',
                (byte) 't',
                (byte) ':',
                (byte) ' ',
                (byte) 'e',
                (byte) 'x',
                (byte) 'a',
                (byte) 'm',
                (byte) 'p',
                (byte) 'l',
                (byte) 'e',
                (byte) '.',
                (byte) 'c',
                (byte) 'o',
                (byte) 'm');
        assertThat(HttpConstants.getString(bytes)).isEqualTo("Host: example.com");
        assertThat(HttpConstants.getString(bytes, 6, 11)).isEqualTo("example.com");
    }

    @Test
    void contentEncodingFallsBackToDefaultCharsetWhenCharsetIsBlankOrUnsupported() {
        byte[] defaultBytes = HttpConstants.getContentBytes("caf\u00E9", "");
        byte[] fallbackBytes = HttpConstants.getContentBytes("caf\u00E9", "not-a-real-charset");

        assertThat(defaultBytes).containsExactly((byte) 'c', (byte) 'a', (byte) 'f', (byte) 0xE9);
        assertThat(fallbackBytes).containsExactly(defaultBytes);
        assertThat(HttpConstants.getContentString(defaultBytes, null)).isEqualTo("caf\u00E9");
        assertThat(HttpConstants.getContentString(defaultBytes, 1, 3, "not-a-real-charset")).isEqualTo("af\u00E9");
    }

    @Test
    void asciiHelpersRejectNullInputAndRoundTripAsciiBytes() {
        byte[] bytes = HttpConstants.getAsciiBytes("GET");

        assertThat(bytes).containsExactly((byte) 'G', (byte) 'E', (byte) 'T');
        assertThat(HttpConstants.getAsciiString(bytes)).isEqualTo("GET");
        assertThat(HttpConstants.getAsciiString(bytes, 1, 2)).isEqualTo("ET");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpConstants.getAsciiBytes(null))
                .withMessage("Parameter may not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpConstants.getAsciiString(null, 0, 0))
                .withMessage("Parameter may not be null");
    }

    private static HttpConstantsClassLoader newHttpConstantsClassLoader() {
        URL location = HttpConstants.class.getProtectionDomain().getCodeSource().getLocation();
        return new HttpConstantsClassLoader(new URL[] {location}, HttpConstantsTest.class.getClassLoader());
    }

    private static final class HttpConstantsClassLoader extends URLClassLoader {
        private HttpConstantsClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_CONSTANTS_CLASS_NAME.equals(name)) {
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
