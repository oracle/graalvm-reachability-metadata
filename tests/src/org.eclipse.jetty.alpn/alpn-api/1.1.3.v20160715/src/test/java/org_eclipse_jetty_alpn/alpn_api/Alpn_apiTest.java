/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_alpn.alpn_api;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import org.eclipse.jetty.alpn.ALPN;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class Alpn_apiTest {

    @Test
    void storesAndRemovesProvidersForSslSockets() throws Exception {
        SSLSocket firstSocket = newSslSocket();
        SSLSocket secondSocket = newSslSocket();
        RecordingClientProvider firstProvider = new RecordingClientProvider(List.of("h2", "http/1.1"));
        RecordingServerProvider secondProvider = new RecordingServerProvider("http/1.1");

        try {
            assertThat(ALPN.get(firstSocket)).isNull();
            assertThat(ALPN.get(secondSocket)).isNull();

            ALPN.put(firstSocket, firstProvider);
            ALPN.put(secondSocket, secondProvider);

            assertThat(ALPN.get(firstSocket)).isSameAs(firstProvider);
            assertThat(ALPN.get(secondSocket)).isSameAs(secondProvider);

            assertThat(ALPN.remove(firstSocket)).isSameAs(firstProvider);
            assertThat(ALPN.get(firstSocket)).isNull();
            assertThat(ALPN.get(secondSocket)).isSameAs(secondProvider);

            assertThat(ALPN.remove(secondSocket)).isSameAs(secondProvider);
            assertThat(ALPN.get(secondSocket)).isNull();
        } finally {
            ALPN.remove(firstSocket);
            ALPN.remove(secondSocket);
            firstSocket.close();
            secondSocket.close();
        }
    }

    @Test
    void replacesExistingProviderForSslSockets() throws Exception {
        SSLSocket socket = newSslSocket();
        RecordingClientProvider initialProvider = new RecordingClientProvider(List.of("h2"));
        RecordingClientProvider replacementProvider = new RecordingClientProvider(List.of("http/1.1"));

        try {
            ALPN.put(socket, initialProvider);
            ALPN.put(socket, replacementProvider);

            assertThat(ALPN.get(socket)).isSameAs(replacementProvider);
            assertThat(ALPN.remove(socket)).isSameAs(replacementProvider);
            assertThat(ALPN.get(socket)).isNull();
        } finally {
            ALPN.remove(socket);
            socket.close();
        }
    }

    @Test
    void storesAndRemovesProvidersForSslEngines() throws Exception {
        SSLEngine firstEngine = newSslEngine();
        SSLEngine secondEngine = newSslEngine();
        RecordingServerProvider firstProvider = new RecordingServerProvider("h2");
        RecordingClientProvider secondProvider = new RecordingClientProvider(List.of("h2", "http/1.1"));

        try {
            assertThat(ALPN.get(firstEngine)).isNull();
            assertThat(ALPN.get(secondEngine)).isNull();

            ALPN.put(firstEngine, firstProvider);
            ALPN.put(secondEngine, secondProvider);

            assertThat(ALPN.get(firstEngine)).isSameAs(firstProvider);
            assertThat(ALPN.get(secondEngine)).isSameAs(secondProvider);

            assertThat(ALPN.remove(firstEngine)).isSameAs(firstProvider);
            assertThat(ALPN.get(firstEngine)).isNull();
            assertThat(ALPN.get(secondEngine)).isSameAs(secondProvider);

            assertThat(ALPN.remove(secondEngine)).isSameAs(secondProvider);
            assertThat(ALPN.get(secondEngine)).isNull();
        } finally {
            ALPN.remove(firstEngine);
            ALPN.remove(secondEngine);
        }
    }

    @Test
    void replacesExistingProviderForSslEngines() throws Exception {
        SSLEngine engine = newSslEngine();
        RecordingServerProvider initialProvider = new RecordingServerProvider("h2");
        RecordingServerProvider replacementProvider = new RecordingServerProvider("http/1.1");

        try {
            ALPN.put(engine, initialProvider);
            ALPN.put(engine, replacementProvider);

            assertThat(ALPN.get(engine)).isSameAs(replacementProvider);
            assertThat(ALPN.remove(engine)).isSameAs(replacementProvider);
            assertThat(ALPN.get(engine)).isNull();
        } finally {
            ALPN.remove(engine);
        }
    }

    @Test
    void rejectsNullArgumentsAcrossAllAccessors() throws Exception {
        SSLSocket socket = newSslSocket();
        SSLEngine engine = newSslEngine();
        RecordingClientProvider provider = new RecordingClientProvider(List.of("h2"));

        try {
            assertThatNullPointerException().isThrownBy(() -> ALPN.put((SSLSocket) null, provider));
            assertThatNullPointerException().isThrownBy(() -> ALPN.put(socket, null));
            assertThatNullPointerException().isThrownBy(() -> ALPN.get((SSLSocket) null));
            assertThatNullPointerException().isThrownBy(() -> ALPN.remove((SSLSocket) null));

            assertThatNullPointerException().isThrownBy(() -> ALPN.put((SSLEngine) null, provider));
            assertThatNullPointerException().isThrownBy(() -> ALPN.put(engine, null));
            assertThatNullPointerException().isThrownBy(() -> ALPN.get((SSLEngine) null));
            assertThatNullPointerException().isThrownBy(() -> ALPN.remove((SSLEngine) null));
        } finally {
            socket.close();
        }
    }

    @Test
    void exposesProviderContractsThroughPublicNestedInterfaces() throws Exception {
        RecordingClientProvider clientProvider = new RecordingClientProvider(List.of("h2", "http/1.1"));
        RecordingServerProvider serverProvider = new RecordingServerProvider("h2");

        assertThat(clientProvider.protocols()).containsExactly("h2", "http/1.1");
        assertThat(clientProvider.wasUnsupportedCalled()).isFalse();
        assertThat(clientProvider.getSelectedProtocol()).isNull();

        clientProvider.selected("h2");
        clientProvider.unsupported();

        assertThat(clientProvider.getSelectedProtocol()).isEqualTo("h2");
        assertThat(clientProvider.wasUnsupportedCalled()).isTrue();

        assertThat(serverProvider.wasUnsupportedCalled()).isFalse();
        assertThat(serverProvider.select(List.of("spdy/3", "h2", "http/1.1"))).isEqualTo("h2");

        serverProvider.unsupported();

        assertThat(serverProvider.wasUnsupportedCalled()).isTrue();
    }

    @Test
    void propagatesSslExceptionsDeclaredByProviderCallbacks() {
        SSLException clientFailure = new SSLException("client callback failure");
        SSLException serverFailure = new SSLException("server callback failure");
        ThrowingClientProvider clientProvider = new ThrowingClientProvider(List.of("h2"), clientFailure);
        ThrowingServerProvider serverProvider = new ThrowingServerProvider(serverFailure);

        SSLException thrownByClient = catchThrowableOfType(() -> clientProvider.selected("h2"), SSLException.class);
        SSLException thrownByServer = catchThrowableOfType(() -> serverProvider.select(List.of("h2", "http/1.1")), SSLException.class);

        assertThat(clientProvider.protocols()).containsExactly("h2");
        assertThat(thrownByClient).isSameAs(clientFailure);
        assertThat(thrownByServer).isSameAs(serverFailure);
    }

    @Test
    void exposesDebugFlagAsMutableGlobalState() {
        boolean originalDebug = ALPN.debug;

        try {
            assertThat(ALPN.debug).isFalse();

            ALPN.debug = true;

            assertThat(ALPN.debug).isTrue();
        } finally {
            ALPN.debug = originalDebug;
        }
    }

    private static SSLSocket newSslSocket() throws Exception {
        return (SSLSocket) newSslContext().getSocketFactory().createSocket();
    }

    private static SSLEngine newSslEngine() throws Exception {
        return newSslContext().createSSLEngine();
    }

    private static SSLContext newSslContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new SecureRandom());
        return sslContext;
    }

    private static final class RecordingClientProvider implements ALPN.ClientProvider {
        private final List<String> protocols;
        private boolean unsupportedCalled;
        private String selectedProtocol;

        private RecordingClientProvider(List<String> protocols) {
            this.protocols = protocols;
        }

        @Override
        public List<String> protocols() {
            return protocols;
        }

        @Override
        public void unsupported() {
            unsupportedCalled = true;
        }

        @Override
        public void selected(String protocol) throws SSLException {
            selectedProtocol = protocol;
        }

        private boolean wasUnsupportedCalled() {
            return unsupportedCalled;
        }

        private String getSelectedProtocol() {
            return selectedProtocol;
        }
    }

    private static final class RecordingServerProvider implements ALPN.ServerProvider {
        private final String preferredProtocol;
        private boolean unsupportedCalled;

        private RecordingServerProvider(String preferredProtocol) {
            this.preferredProtocol = preferredProtocol;
        }

        @Override
        public void unsupported() {
            unsupportedCalled = true;
        }

        @Override
        public String select(List<String> protocols) throws SSLException {
            if (protocols.contains(preferredProtocol)) {
                return preferredProtocol;
            }
            return protocols.get(0);
        }

        private boolean wasUnsupportedCalled() {
            return unsupportedCalled;
        }
    }

    private static final class ThrowingClientProvider implements ALPN.ClientProvider {
        private final List<String> protocols;
        private final SSLException failure;

        private ThrowingClientProvider(List<String> protocols, SSLException failure) {
            this.protocols = protocols;
            this.failure = failure;
        }

        @Override
        public List<String> protocols() {
            return protocols;
        }

        @Override
        public void unsupported() {
        }

        @Override
        public void selected(String protocol) throws SSLException {
            throw failure;
        }
    }

    private static final class ThrowingServerProvider implements ALPN.ServerProvider {
        private final SSLException failure;

        private ThrowingServerProvider(SSLException failure) {
            this.failure = failure;
        }

        @Override
        public void unsupported() {
        }

        @Override
        public String select(List<String> protocols) throws SSLException {
            throw failure;
        }
    }
}
