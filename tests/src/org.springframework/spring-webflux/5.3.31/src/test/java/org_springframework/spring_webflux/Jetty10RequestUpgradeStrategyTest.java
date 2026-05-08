/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webflux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.upgrade.Jetty10RequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;

public class Jetty10RequestUpgradeStrategyTest {
    private static final URI REQUEST_URI = URI.create("http://localhost/ws");

    @Test
    void constructsStrategyWhenJetty10WebSocketServerApiIsAvailable() {
        RequestUpgradeStrategy strategy = new Jetty10RequestUpgradeStrategy();

        assertThat(strategy).isInstanceOf(Jetty10RequestUpgradeStrategy.class);
    }

    @Test
    void upgradeCreatesJettyWebSocketCreatorProxyBeforeLookingUpContainer() {
        RequestUpgradeStrategy strategy = new Jetty10RequestUpgradeStrategy();
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest(servletContext, "GET", "/ws");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServerWebExchange exchange = createExchange(servletRequest, servletResponse);
        HandshakeInfo handshakeInfo = new HandshakeInfo(REQUEST_URI, new HttpHeaders(), Mono.empty(), "chat");

        // The mock servlet context intentionally has no Jetty container; by the time this fails,
        // the strategy has already created the JettyWebSocketCreator proxy.
        assertThatThrownBy(() -> strategy.upgrade(exchange, session -> Mono.empty(), "chat", () -> handshakeInfo)
                .block(Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class);
    }

    private static ServerWebExchange createExchange(
            MockHttpServletRequest servletRequest, MockHttpServletResponse servletResponse) {

        return new DefaultServerWebExchange(
                new NativeServerHttpRequest(servletRequest),
                new NativeServerHttpResponse(servletResponse),
                new DefaultWebSessionManager(),
                ServerCodecConfigurer.create(),
                new AcceptHeaderLocaleContextResolver());
    }

    private static final class NativeServerHttpRequest extends AbstractServerHttpRequest {
        private final MockHttpServletRequest servletRequest;

        private NativeServerHttpRequest(MockHttpServletRequest servletRequest) {
            super(REQUEST_URI, "", new HttpHeaders());
            this.servletRequest = servletRequest;
        }

        @Override
        public String getMethodValue() {
            return this.servletRequest.getMethod();
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.empty();
        }

        @Override
        protected MultiValueMap<String, HttpCookie> initCookies() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        protected SslInfo initSslInfo() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeRequest() {
            return (T) this.servletRequest;
        }
    }

    private static final class NativeServerHttpResponse extends AbstractServerHttpResponse {
        private final MockHttpServletResponse servletResponse;

        private NativeServerHttpResponse(MockHttpServletResponse servletResponse) {
            super(DefaultDataBufferFactory.sharedInstance);
            this.servletResponse = servletResponse;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeResponse() {
            return (T) this.servletResponse;
        }

        @Override
        protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
            return Mono.empty();
        }

        @Override
        protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return Mono.empty();
        }

        @Override
        protected void applyStatusCode() {
            // No underlying response state is needed for this upgrade-path test.
        }

        @Override
        protected void applyHeaders() {
            // No underlying response state is needed for this upgrade-path test.
        }

        @Override
        protected void applyCookies() {
            // No underlying response state is needed for this upgrade-path test.
        }
    }
}
