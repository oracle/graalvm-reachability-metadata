/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.Address;
import okhttp3.Authenticator;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

public class ClientConfigurationApiCoverageTest {
    @Test
    void clientBuilderPublishesAllConfiguredPolicies() throws Exception {
        CacheFixture cacheFixture = new CacheFixture();
        ConnectionPool pool = new ConnectionPool(4, 1, TimeUnit.MINUTES);
        Dispatcher dispatcher = new Dispatcher(Executors.newSingleThreadExecutor());
        Interceptor interceptor = chain -> chain.proceed(chain.request());
        Dns dns = hostname -> Collections.singletonList(java.net.InetAddress.getLoopbackAddress());
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new java.net.InetSocketAddress("localhost", 8080));
        ProxySelector selector = new ProxySelector() {
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }

            public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) {
            }
        };
        Authenticator authenticator = (route, response) -> null;
        HostnameVerifier verifier = (hostname, session) -> hostname.equals("example.com");
        SSLSocketFactory sslFactory = SSLContext.getDefault().getSocketFactory();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .proxy(proxy)
                .proxySelector(selector)
                .cookieJar(CookieJar.NO_COOKIES)
                .cache(cacheFixture.cache)
                .dns(dns)
                .socketFactory(SocketFactory.getDefault())
                .sslSocketFactory(sslFactory, new TrustManagerFixture())
                .hostnameVerifier(verifier)
                .certificatePinner(CertificatePinner.DEFAULT)
                .authenticator(authenticator)
                .proxyAuthenticator(authenticator)
                .connectionPool(pool)
                .followSslRedirects(false)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .dispatcher(dispatcher)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionSpecs(Collections.singletonList(ConnectionSpec.CLEARTEXT))
                .addInterceptor(interceptor)
                .addNetworkInterceptor(interceptor)
                .build();

        assertThat(client.connectTimeoutMillis()).isEqualTo(2000);
        assertThat(client.readTimeoutMillis()).isEqualTo(3000);
        assertThat(client.writeTimeoutMillis()).isEqualTo(4000);
        assertThat(client.pingIntervalMillis()).isEqualTo(5000);
        assertThat(client.proxy()).isEqualTo(proxy);
        assertThat(client.proxySelector()).isSameAs(selector);
        assertThat(client.cookieJar()).isSameAs(CookieJar.NO_COOKIES);
        assertThat(client.cache()).isSameAs(cacheFixture.cache);
        assertThat(client.dns()).isSameAs(dns);
        assertThat(client.socketFactory()).isSameAs(SocketFactory.getDefault());
        assertThat(client.sslSocketFactory()).isSameAs(sslFactory);
        assertThat(client.hostnameVerifier()).isSameAs(verifier);
        assertThat(client.certificatePinner()).isNotNull();
        assertThat(client.authenticator()).isSameAs(authenticator);
        assertThat(client.proxyAuthenticator()).isSameAs(authenticator);
        assertThat(client.connectionPool()).isSameAs(pool);
        assertThat(client.followSslRedirects()).isFalse();
        assertThat(client.followRedirects()).isFalse();
        assertThat(client.retryOnConnectionFailure()).isFalse();
        assertThat(client.dispatcher()).isSameAs(dispatcher);
        assertThat(client.protocols()).containsExactly(Protocol.HTTP_1_1);
        assertThat(client.connectionSpecs()).containsExactly(ConnectionSpec.CLEARTEXT);
        assertThat(client.interceptors()).containsExactly(interceptor);
        assertThat(client.networkInterceptors()).containsExactly(interceptor);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(interceptor).addNetworkInterceptor(interceptor);
        assertThat(builder.interceptors()).containsExactly(interceptor);
        assertThat(builder.networkInterceptors()).containsExactly(interceptor);

        Request request = new Request.Builder().url("http://example.com/").build();
        assertThat(client.newCall(request).request()).isSameAs(request);
        try {
            OkHttpClient oneArgumentSslClient = new OkHttpClient.Builder()
                    .sslSocketFactory(sslFactory).build();
            assertThat(oneArgumentSslClient.sslSocketFactory()).isSameAs(sslFactory);
        } catch (RuntimeException expectedOnStronglyEncapsulatedJdk) {
            assertThat(expectedOnStronglyEncapsulatedJdk.getMessage()).isNotNull();
        }
        assertThat(client.newWebSocket(request, new okhttp3.WebSocketListener() { })).isNotNull();
        OkHttpClient copied = client.newBuilder().build();
        assertThat(copied.connectionPool()).isSameAs(pool);
        assertThat(copied.dispatcher()).isSameAs(dispatcher);
        dispatcher.executorService().shutdownNow();
        cacheFixture.cache.delete();
    }

    @Test
    void addressesAndConnectionSpecsDescribeRoutesAndTlsCompatibility() throws Exception {
        ConnectionSpec configured = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .cipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
                .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
                .supportsTlsExtensions(true)
                .build();
        assertThat(configured.isTls()).isTrue();
        assertThat(configured.cipherSuites()).containsExactly(
                okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256);
        assertThat(configured.tlsVersions()).containsExactly(okhttp3.TlsVersion.TLS_1_2);
        assertThat(configured.supportsTlsExtensions()).isTrue();
        assertThat(configured.toString()).contains("TLS_1_2");
        assertThat(configured).isEqualTo(new ConnectionSpec.Builder(configured)
                .cipherSuites(okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
                .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
                .supportsTlsExtensions(true)
                .build());
        ConnectionSpec allEnabled = new ConnectionSpec.Builder(configured)
                .allEnabledCipherSuites().allEnabledTlsVersions().build();
        assertThat(allEnabled.cipherSuites()).isNull();
        assertThat(allEnabled.tlsVersions()).isNull();
        assertThat(configured.hashCode()).isEqualTo(configured.hashCode());
        assertThat(ConnectionSpec.CLEARTEXT.isTls()).isFalse();
        assertThat(ConnectionSpec.CLEARTEXT.toString()).isEqualTo("ConnectionSpec()");

        SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket();
        assertThat(configured.isCompatible(socket)).isTrue();

        HostnameVerifier verifier = (hostname, session) -> true;
        ConnectionPool pool = new ConnectionPool();
        Address address = new Address("example.com", 443, Dns.SYSTEM, SocketFactory.getDefault(),
                SSLContext.getDefault().getSocketFactory(), verifier, CertificatePinner.DEFAULT,
                Authenticator.NONE, null, Arrays.asList(Protocol.HTTP_1_1),
                Arrays.asList(configured), ProxySelector.getDefault());
        assertThat(address.url().toString()).isEqualTo("https://example.com/");
        assertThat(address.dns()).isSameAs(Dns.SYSTEM);
        assertThat(address.socketFactory()).isSameAs(SocketFactory.getDefault());
        assertThat(address.sslSocketFactory()).isNotNull();
        assertThat(address.hostnameVerifier()).isSameAs(verifier);
        assertThat(address.certificatePinner()).isEqualTo(CertificatePinner.DEFAULT);
        assertThat(address.proxyAuthenticator()).isSameAs(Authenticator.NONE);
        assertThat(address.protocols()).containsExactly(Protocol.HTTP_1_1);
        assertThat(address.connectionSpecs()).containsExactly(configured);
        assertThat(address.proxySelector()).isSameAs(ProxySelector.getDefault());
        assertThat(address.proxy()).isNull();
        assertThat(address).isEqualTo(address);
        assertThat(address.hashCode()).isEqualTo(address.hashCode());
        assertThat(address.toString()).contains("example.com:443", "proxySelector");
        pool.evictAll();
    }

    @Test
    void dispatchersAndPoolsExposeResourceLifecycle() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Dispatcher dispatcher = new Dispatcher(executor);
        assertThat(dispatcher.executorService()).isSameAs(executor);
        assertThat(dispatcher.getMaxRequests()).isEqualTo(64);
        assertThat(dispatcher.getMaxRequestsPerHost()).isEqualTo(5);
        dispatcher.setMaxRequests(2);
        dispatcher.setMaxRequestsPerHost(1);
        dispatcher.setIdleCallback(() -> { });
        assertThat(dispatcher.getMaxRequests()).isEqualTo(2);
        assertThat(dispatcher.getMaxRequestsPerHost()).isEqualTo(1);
        assertThat(dispatcher.queuedCalls()).isEmpty();
        assertThat(dispatcher.runningCalls()).isEmpty();
        assertThat(dispatcher.queuedCallsCount()).isZero();
        assertThat(dispatcher.runningCallsCount()).isZero();
        dispatcher.cancelAll();
        executor.shutdownNow();

        ConnectionPool defaultPool = new ConnectionPool();
        ConnectionPool configuredPool = new ConnectionPool(2, 10, TimeUnit.SECONDS);
        assertThat(defaultPool.connectionCount()).isZero();
        assertThat(defaultPool.idleConnectionCount()).isZero();
        defaultPool.evictAll();
        assertThat(configuredPool.connectionCount()).isZero();
        assertThat(configuredPool.idleConnectionCount()).isZero();
        configuredPool.evictAll();
    }

    private static final class CacheFixture {
        private final okhttp3.Cache cache;

        private CacheFixture() throws java.io.IOException {
            cache = new okhttp3.Cache(java.nio.file.Files.createTempDirectory("okhttp-cache").toFile(),
                    1024L);
        }
    }

    private static final class TrustManagerFixture implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
