/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpStateTest {
    @Test
    void initializesHttpStateInFreshClassLoader() throws Exception {
        try {
            HttpStateClassLoader classLoader = new HttpStateClassLoader(HttpState.class.getClassLoader());

            Class<?> httpStateClass = Class.forName(HttpStateClassLoader.HTTP_STATE_CLASS_NAME, true, classLoader);

            assertThat(httpStateClass.getName()).isEqualTo(HttpStateClassLoader.HTTP_STATE_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(httpStateClass).isSameAs(HttpState.class);
            } else {
                assertThat(httpStateClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void storesAndMatchesHostCredentials() {
        HttpState state = new HttpState();
        Credentials exactCredentials = new UsernamePasswordCredentials("alice", "secret");
        Credentials fallbackCredentials = new UsernamePasswordCredentials("fallback", "secret");
        AuthScope exactScope = new AuthScope("example.com", 80, "private", "basic");
        AuthScope hostScope = new AuthScope(
            "example.com",
            AuthScope.ANY_PORT,
            AuthScope.ANY_REALM,
            AuthScope.ANY_SCHEME
        );

        state.setCredentials(hostScope, fallbackCredentials);
        state.setCredentials(exactScope, exactCredentials);

        assertThat(state.getCredentials(new AuthScope("example.com", 80, "private", "basic")))
            .isSameAs(exactCredentials);
        assertThat(state.getCredentials(new AuthScope("example.com", 443, "other", "digest")))
            .isSameAs(fallbackCredentials);
    }

    @Test
    void storesProxyCredentialsSeparatelyFromTargetCredentials() {
        HttpState state = new HttpState();
        Credentials targetCredentials = new UsernamePasswordCredentials("target", "secret");
        Credentials proxyCredentials = new UsernamePasswordCredentials("proxy", "secret");
        AuthScope targetScope = new AuthScope("example.com", 80, "private", "basic");
        AuthScope proxyScope = new AuthScope("proxy.example.com", 8080, "proxy", "basic");

        state.setCredentials(targetScope, targetCredentials);
        state.setProxyCredentials(proxyScope, proxyCredentials);

        assertThat(state.getCredentials(targetScope)).isSameAs(targetCredentials);
        assertThat(state.getProxyCredentials(proxyScope)).isSameAs(proxyCredentials);
        assertThat(state.getCredentials(proxyScope)).isNull();
        assertThat(state.getProxyCredentials(targetScope)).isNull();
    }

    @Test
    void clearRemovesState() {
        HttpState state = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials("alice", "secret");
        AuthScope scope = new AuthScope("example.com", 80, "private", "basic");

        state.setCredentials(scope, credentials);
        state.setProxyCredentials(scope, credentials);

        state.clear();

        assertThat(state.getCredentials(scope)).isNull();
        assertThat(state.getProxyCredentials(scope)).isNull();
        assertThat(state.getCookies()).isEmpty();
    }

    private static final class HttpStateClassLoader extends ClassLoader {
        private static final String HTTP_STATE_CLASS_NAME = "org.apache.commons.httpclient.HttpState";
        private static final String HTTP_STATE_RESOURCE_NAME = "org/apache/commons/httpclient/HttpState.class";

        private HttpStateClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!HTTP_STATE_CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineHttpStateClass();
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineHttpStateClass() throws ClassNotFoundException {
            try {
                byte[] classBytes = readHttpStateClassBytes();
                return defineClass(HTTP_STATE_CLASS_NAME, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(HTTP_STATE_CLASS_NAME, exception);
            }
        }

        private byte[] readHttpStateClassBytes() throws IOException, ClassNotFoundException {
            try (InputStream inputStream = getParent().getResourceAsStream(HTTP_STATE_RESOURCE_NAME)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(HTTP_STATE_CLASS_NAME);
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesRead = inputStream.read(buffer);
                }
                return outputStream.toByteArray();
            }
        }
    }
}
