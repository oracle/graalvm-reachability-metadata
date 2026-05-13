/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.ExpectContinueMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ExpectContinueMethodTest {
    private static final String EXPECT_CONTINUE_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.ExpectContinueMethod";

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (ExpectContinueMethodClassLoader classLoader = newExpectContinueMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    EXPECT_CONTINUE_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(EXPECT_CONTINUE_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(ExpectContinueMethod.class);
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
    void subclassInitializationAndRequestHeaderHandlingExerciseExpectContinueMethod()
            throws Exception {
        TestExpectContinueMethod method = new TestExpectContinueMethod("/upload", true);

        assertThat(method).isInstanceOf(ExpectContinueMethod.class);
        assertThat(method.getUseExpectHeader()).isFalse();

        method.setUseExpectHeader(true);
        method.addHeaders(new HttpState(), new HttpConnection("example.com", 80));

        Header expectHeader = method.getRequestHeader("Expect");
        assertThat(method.getUseExpectHeader()).isTrue();
        assertThat(expectHeader).isNotNull();
        assertThat(expectHeader.getValue()).isEqualTo("100-continue");

        method.setRequestContent(false);
        method.addHeaders(new HttpState(), new HttpConnection("example.com", 80));

        assertThat(method.getRequestHeader("Expect")).isNull();
    }

    @Test
    void expectHeaderRequiresHttp11OrNewer() throws Exception {
        TestExpectContinueMethod method = new TestExpectContinueMethod("/upload", true);
        method.setUseExpectHeader(true);
        method.getParams().setVersion(HttpVersion.HTTP_1_0);

        method.addHeaders(new HttpState(), new HttpConnection("example.com", 80));

        assertThat(method.getRequestHeader("Expect")).isNull();
    }

    private static ExpectContinueMethodClassLoader newExpectContinueMethodClassLoader() {
        URL location = ExpectContinueMethod.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new ExpectContinueMethodClassLoader(
                new URL[] {location},
                ExpectContinueMethodTest.class.getClassLoader());
    }

    private static final class ExpectContinueMethodClassLoader extends URLClassLoader {
        private ExpectContinueMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (EXPECT_CONTINUE_METHOD_CLASS_NAME.equals(name)) {
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

    private static final class TestExpectContinueMethod extends ExpectContinueMethod {
        private boolean requestContent;

        private TestExpectContinueMethod(String uri, boolean requestContent) {
            super(uri);
            this.requestContent = requestContent;
        }

        @Override
        public String getName() {
            return "TEST";
        }

        @Override
        public HttpVersion getEffectiveVersion() {
            return getParams().getVersion();
        }

        @Override
        protected boolean hasRequestContent() {
            return requestContent;
        }

        private void setRequestContent(boolean requestContent) {
            this.requestContent = requestContent;
        }

        private void addHeaders(HttpState state, HttpConnection connection)
                throws IOException, HttpException {
            addRequestHeaders(state, connection);
        }
    }
}
