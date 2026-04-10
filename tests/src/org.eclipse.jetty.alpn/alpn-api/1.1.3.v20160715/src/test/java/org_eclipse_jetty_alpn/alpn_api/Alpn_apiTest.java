/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_alpn.alpn_api;

import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import org.eclipse.jetty.alpn.ALPN;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Alpn_apiTest {
    @Test
    void storesAndRemovesClientProviderForSslSocket() throws Exception {
        final SSLSocket socket = newSslSocket();
        final RecordingClientProvider provider = new RecordingClientProvider(List.of("h2", "http/1.1"));

        try {
            assertThat(ALPN.get(socket)).isNull();

            ALPN.put(socket, provider);

            final ALPN.Provider storedProvider = ALPN.get(socket);
            assertThat(storedProvider).isSameAs(provider);
            assertThat(((ALPN.ClientProvider) storedProvider).protocols()).containsExactly("h2", "http/1.1");

            ((ALPN.ClientProvider) storedProvider).selected("h2");
            ((ALPN.ClientProvider) storedProvider).unsupported();

            assertThat(provider.getSelectedProtocol()).isEqualTo("h2");
            assertThat(provider.isUnsupportedCalled()).isTrue();
            assertThat(ALPN.remove(socket)).isSameAs(provider);
            assertThat(ALPN.get(socket)).isNull();
        } finally {
            ALPN.remove(socket);
            socket.close();
        }
    }

    @Test
    void putReplacesPreviouslyRegisteredProviderForSslSocket() throws Exception {
        final SSLSocket socket = newSslSocket();
        final RecordingClientProvider initialProvider = new RecordingClientProvider(List.of("http/1.1"));
        final RecordingClientProvider replacementProvider = new RecordingClientProvider(List.of("h2"));

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
    void storesAndRemovesServerProviderForSslEngine() throws Exception {
        final SSLEngine engine = newSslEngine();
        final RecordingServerProvider provider = new RecordingServerProvider("h2");

        try {
            assertThat(ALPN.get(engine)).isNull();

            ALPN.put(engine, provider);

            final ALPN.Provider storedProvider = ALPN.get(engine);
            assertThat(storedProvider).isSameAs(provider);
            assertThat(((ALPN.ServerProvider) storedProvider).select(List.of("h2", "http/1.1"))).isEqualTo("h2");

            ((ALPN.ServerProvider) storedProvider).unsupported();

            assertThat(provider.getOfferedProtocols()).containsExactly("h2", "http/1.1");
            assertThat(provider.isUnsupportedCalled()).isTrue();
            assertThat(ALPN.remove(engine)).isSameAs(provider);
            assertThat(ALPN.get(engine)).isNull();
        } finally {
            ALPN.remove(engine);
        }
    }

    @Test
    void keepsSocketAndEngineRegistrationsIndependentAcrossProviderTypes() throws Exception {
        final SSLSocket socket = newSslSocket();
        final SSLEngine engine = newSslEngine();
        final RecordingServerProvider socketProvider = new RecordingServerProvider("http/1.1");
        final RecordingClientProvider engineProvider = new RecordingClientProvider(List.of("h2", "http/1.1"));

        try {
            ALPN.put(socket, socketProvider);
            ALPN.put(engine, engineProvider);

            assertThat(ALPN.get(socket)).isSameAs(socketProvider);
            assertThat(ALPN.get(engine)).isSameAs(engineProvider);
            assertThat(((ALPN.ServerProvider) ALPN.get(socket)).select(List.of("http/1.1", "h2"))).isEqualTo("http/1.1");
            assertThat(((ALPN.ClientProvider) ALPN.get(engine)).protocols()).containsExactly("h2", "http/1.1");

            assertThat(ALPN.remove(socket)).isSameAs(socketProvider);
            assertThat(ALPN.get(socket)).isNull();
            assertThat(ALPN.get(engine)).isSameAs(engineProvider);

            ((ALPN.ClientProvider) ALPN.get(engine)).selected("h2");

            assertThat(socketProvider.getOfferedProtocols()).containsExactly("http/1.1", "h2");
            assertThat(engineProvider.getSelectedProtocol()).isEqualTo("h2");
            assertThat(ALPN.remove(engine)).isSameAs(engineProvider);
            assertThat(ALPN.get(engine)).isNull();
        } finally {
            ALPN.remove(socket);
            socket.close();
            ALPN.remove(engine);
        }
    }

    @Test
    void keepsRegistrationsIndependentAcrossMultipleSslSockets() throws Exception {
        final SSLSocket firstSocket = newSslSocket();
        final SSLSocket secondSocket = newSslSocket();
        final RecordingClientProvider firstProvider = new RecordingClientProvider(List.of("h2", "http/1.1"));
        final RecordingServerProvider secondProvider = new RecordingServerProvider("http/1.1");

        try {
            ALPN.put(firstSocket, firstProvider);
            ALPN.put(secondSocket, secondProvider);

            assertThat(ALPN.get(firstSocket)).isSameAs(firstProvider);
            assertThat(ALPN.get(secondSocket)).isSameAs(secondProvider);

            ((ALPN.ClientProvider) ALPN.get(firstSocket)).selected("h2");
            assertThat(((ALPN.ServerProvider) ALPN.get(secondSocket)).select(List.of("h2", "http/1.1")))
                    .isEqualTo("http/1.1");

            assertThat(firstProvider.getSelectedProtocol()).isEqualTo("h2");
            assertThat(secondProvider.getOfferedProtocols()).containsExactly("h2", "http/1.1");
            assertThat(ALPN.remove(firstSocket)).isSameAs(firstProvider);
            assertThat(ALPN.get(firstSocket)).isNull();
            assertThat(ALPN.get(secondSocket)).isSameAs(secondProvider);
            assertThat(ALPN.remove(secondSocket)).isSameAs(secondProvider);
            assertThat(ALPN.get(secondSocket)).isNull();
        } finally {
            ALPN.remove(firstSocket);
            firstSocket.close();
            ALPN.remove(secondSocket);
            secondSocket.close();
        }
    }

    @Test
    void rejectsNullEndpointsAndNullProviders() throws Exception {
        final SSLSocket socket = newSslSocket();
        final SSLEngine engine = newSslEngine();
        final RecordingClientProvider clientProvider = new RecordingClientProvider(List.of("h2"));

        try {
            assertThatThrownBy(() -> ALPN.put((SSLSocket) null, clientProvider))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.get((SSLSocket) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.remove((SSLSocket) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.put(socket, null))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> ALPN.put((SSLEngine) null, clientProvider))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.get((SSLEngine) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.remove((SSLEngine) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ALPN.put(engine, null))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            ALPN.remove(socket);
            socket.close();
            ALPN.remove(engine);
        }
    }

    private SSLSocket newSslSocket() throws Exception {
        final SSLContext sslContext = SSLContext.getDefault();
        return (SSLSocket) sslContext.getSocketFactory().createSocket();
    }

    private SSLEngine newSslEngine() throws Exception {
        final SSLContext sslContext = SSLContext.getDefault();
        return sslContext.createSSLEngine();
    }

    private static final class RecordingClientProvider implements ALPN.ClientProvider {
        private final List<String> protocols;
        private boolean unsupportedCalled;
        private String selectedProtocol;

        private RecordingClientProvider(final List<String> protocols) {
            this.protocols = List.copyOf(protocols);
        }

        @Override
        public List<String> protocols() {
            return this.protocols;
        }

        @Override
        public void unsupported() {
            this.unsupportedCalled = true;
        }

        @Override
        public void selected(final String protocol) throws SSLException {
            this.selectedProtocol = protocol;
        }

        private boolean isUnsupportedCalled() {
            return this.unsupportedCalled;
        }

        private String getSelectedProtocol() {
            return this.selectedProtocol;
        }
    }

    private static final class RecordingServerProvider implements ALPN.ServerProvider {
        private final String selectedProtocol;
        private final List<String> offeredProtocols = new ArrayList<>();
        private boolean unsupportedCalled;

        private RecordingServerProvider(final String selectedProtocol) {
            this.selectedProtocol = selectedProtocol;
        }

        @Override
        public void unsupported() {
            this.unsupportedCalled = true;
        }

        @Override
        public String select(final List<String> protocols) throws SSLException {
            this.offeredProtocols.clear();
            this.offeredProtocols.addAll(protocols);
            return this.selectedProtocol;
        }

        private List<String> getOfferedProtocols() {
            return List.copyOf(this.offeredProtocols);
        }

        private boolean isUnsupportedCalled() {
            return this.unsupportedCalled;
        }
    }
}
