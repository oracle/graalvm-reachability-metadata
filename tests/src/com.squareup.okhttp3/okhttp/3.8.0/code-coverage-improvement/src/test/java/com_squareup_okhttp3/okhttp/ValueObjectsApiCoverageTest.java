/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.lang.reflect.Proxy;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import okhttp3.CacheControl;
import okhttp3.CertificatePinner;
import okhttp3.Challenge;
import okhttp3.CipherSuite;
import okhttp3.Cookie;
import okhttp3.Credentials;
import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;

public class ValueObjectsApiCoverageTest {
    @Test
    void cacheControlHeadersAndAuthenticationValuesRoundTrip() throws Exception {
        CacheControl configured = new CacheControl.Builder()
                .noCache()
                .noStore()
                .maxAge(2, TimeUnit.MINUTES)
                .maxStale(3, TimeUnit.SECONDS)
                .minFresh(4, TimeUnit.SECONDS)
                .onlyIfCached()
                .noTransform()
                .immutable()
                .build();
        assertThat(configured.noCache()).isTrue();
        assertThat(configured.noStore()).isTrue();
        assertThat(configured.maxAgeSeconds()).isEqualTo(120);
        assertThat(configured.maxStaleSeconds()).isEqualTo(3);
        assertThat(configured.minFreshSeconds()).isEqualTo(4);
        assertThat(configured.onlyIfCached()).isTrue();
        assertThat(configured.noTransform()).isTrue();
        assertThat(configured.immutable()).isTrue();
        assertThat(configured.toString()).contains("no-cache", "immutable");

        Headers headers = new Headers.Builder()
                .add("Cache-Control", "public, max-age=60, s-maxage=120, must-revalidate")
                .add("Date", "Wed, 21 Oct 2015 07:28:00 GMT")
                .add("X-Test", "one")
                .add("X-Test", "two")
                .build();
        CacheControl parsed = CacheControl.parse(headers);
        assertThat(parsed.isPublic()).isTrue();
        assertThat(parsed.isPrivate()).isFalse();
        assertThat(parsed.maxAgeSeconds()).isEqualTo(60);
        assertThat(parsed.sMaxAgeSeconds()).isEqualTo(120);
        assertThat(parsed.mustRevalidate()).isTrue();

        assertThat(headers.size()).isEqualTo(4);
        assertThat(headers.get("x-test")).isEqualTo("two");
        assertThat(headers.values("X-Test")).containsExactly("one", "two");
        assertThat(headers.name(0)).isEqualTo("Cache-Control");
        assertThat(headers.value(0)).contains("public");
        assertThat(headers.names()).contains("Date", "X-Test");
        assertThat(headers.getDate("Date")).isEqualTo(new Date(1445412480000L));
        assertThat(headers.toMultimap().get("X-Test")).containsExactly("one", "two");
        assertThat(headers.toString()).contains("Cache-Control");
        assertThat(headers).isEqualTo(Headers.of("Cache-Control", headers.get("Cache-Control"),
                "Date", headers.get("Date"), "X-Test", "one", "X-Test", "two"));
        assertThat(headers.hashCode()).isEqualTo(headers.hashCode());

        Headers fromMap = Headers.of(Collections.singletonMap("X-Map", "value"));
        assertThat(fromMap.get("X-Map")).isEqualTo("value");
        Headers edited = headers.newBuilder()
                .set("X-Test", "replacement")
                .removeAll("Date")
                .add("Created", "yes")
                .build();
        assertThat(edited.get("X-Test")).isEqualTo("replacement");
        assertThat(edited.get("Date")).isNull();
        Headers.Builder builder = new Headers.Builder().add("Standalone", "value");
        assertThat(builder.get("Standalone")).isEqualTo("value");
        assertThat(builder.build().get("Standalone")).isEqualTo("value");
        assertThat(new Headers.Builder().add("Combined: value").build().get("Combined"))
                .isEqualTo("value");

        assertThat(Credentials.basic("user", "password")).isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
        assertThat(Credentials.basic("user", "password", Charset.forName("UTF-8")))
                .isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
        assertThat(Protocol.get("h2")).isEqualTo(Protocol.HTTP_2);
        assertThat(Protocol.valueOf("HTTP_1_1")).isEqualTo(Protocol.HTTP_1_1);
        assertThat(Protocol.values()).containsExactly(Protocol.HTTP_1_0, Protocol.HTTP_1_1,
                Protocol.SPDY_3, Protocol.HTTP_2);
        assertThat(okhttp3.TlsVersion.valueOf("TLS_1_2"))
                .isEqualTo(okhttp3.TlsVersion.TLS_1_2);
        assertThat(okhttp3.TlsVersion.values()).containsExactly(okhttp3.TlsVersion.TLS_1_3,
                okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_1,
                okhttp3.TlsVersion.TLS_1_0, okhttp3.TlsVersion.SSL_3_0);
    }

    @Test
    void cookiesAndChallengesExposePolicyAndIdentity() {
        long expiry = 1700000000000L;
        Cookie hostCookie = new Cookie.Builder()
                .name("sid")
                .value("abc")
                .hostOnlyDomain("example.com")
                .path("/account")
                .expiresAt(expiry)
                .httpOnly()
                .secure()
                .build();
        assertThat(hostCookie.name()).isEqualTo("sid");
        assertThat(hostCookie.value()).isEqualTo("abc");
        assertThat(hostCookie.domain()).isEqualTo("example.com");
        assertThat(hostCookie.path()).isEqualTo("/account");
        assertThat(hostCookie.expiresAt()).isEqualTo(expiry);
        assertThat(hostCookie.persistent()).isTrue();
        assertThat(hostCookie.hostOnly()).isTrue();
        assertThat(hostCookie.httpOnly()).isTrue();
        assertThat(hostCookie.secure()).isTrue();
        assertThat(hostCookie.matches(okhttp3.HttpUrl.parse("https://example.com/account/view")))
                .isTrue();
        assertThat(hostCookie.matches(okhttp3.HttpUrl.parse("http://example.com/account/view")))
                .isFalse();
        Cookie domainCookie = new Cookie.Builder().name("domain").value("value")
                .domain("example.com").build();
        assertThat(domainCookie.hostOnly()).isFalse();
        assertThat(hostCookie.toString()).contains("sid=abc", "expires=");
        assertThat(hostCookie).isEqualTo(new Cookie.Builder().name("sid").value("abc")
                .hostOnlyDomain("example.com").path("/account").expiresAt(expiry).httpOnly()
                .secure().build());
        assertThat(hostCookie.hashCode()).isEqualTo(hostCookie.hashCode());

        Cookie parsed = Cookie.parse(okhttp3.HttpUrl.parse("https://www.example.com/account"),
                "theme=dark; Expires=Wed, 21 Oct 2015 07:28:00 GMT; Max-Age=3600; "
                        + "Domain=example.com; Path=/account; Secure; HttpOnly");
        assertThat(parsed).isNotNull();
        assertThat(parsed.domain()).isEqualTo("example.com");
        assertThat(parsed.hostOnly()).isFalse();
        assertThat(parsed.matches(okhttp3.HttpUrl.parse("https://www.example.com/account/home")))
                .isTrue();
        Headers setCookie = Headers.of("Set-Cookie", "a=1; Path=/", "Set-Cookie", "b=2; Path=/");
        assertThat(Cookie.parseAll(okhttp3.HttpUrl.parse("http://example.com/"), setCookie))
                .extracting(Cookie::name).containsExactly("a", "b");

        Challenge challenge = new Challenge("Basic", "users");
        assertThat(challenge.scheme()).isEqualTo("Basic");
        assertThat(challenge.realm()).isEqualTo("users");
        assertThat(challenge.toString()).contains("Basic", "users");
        assertThat(challenge).isEqualTo(new Challenge("Basic", "users"));
        assertThat(challenge.hashCode()).isEqualTo(new Challenge("Basic", "users").hashCode());
    }

    @Test
    void mediaTlsHandshakeAndCertificatePoliciesHaveStableValues() throws Exception {
        MediaType json = MediaType.parse("Application/JSON; charset=utf-8");
        assertThat(json.type()).isEqualTo("application");
        assertThat(json.subtype()).isEqualTo("json");
        assertThat(json.charset()).isEqualTo(Charset.forName("UTF-8"));
        assertThat(json.charset(Charset.defaultCharset())).isEqualTo(Charset.forName("UTF-8"));
        assertThat(json.toString()).isEqualTo("Application/JSON; charset=utf-8");
        assertThat(json).isEqualTo(MediaType.parse("Application/JSON; charset=utf-8"));
        assertThat(json.hashCode()).isEqualTo(MediaType.parse("Application/JSON; charset=utf-8").hashCode());

        CipherSuite suite = CipherSuite.forJavaName("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        assertThat(suite.javaName()).isEqualTo("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        assertThat(suite.toString()).isEqualTo(suite.javaName());
        assertThat(TlsVersionShim.name()).isEqualTo("TLSv1.2");

        Handshake handshake = Handshake.get(okhttp3.TlsVersion.TLS_1_2, suite,
                Collections.<Certificate>emptyList(), Collections.<Certificate>emptyList());
        assertThat(handshake.tlsVersion()).isEqualTo(okhttp3.TlsVersion.TLS_1_2);
        assertThat(handshake.cipherSuite()).isEqualTo(suite);
        assertThat(handshake.peerCertificates()).isEmpty();
        assertThat(handshake.localCertificates()).isEmpty();
        assertThat(handshake.peerPrincipal()).isNull();
        assertThat(handshake.localPrincipal()).isNull();
        assertThat(handshake).isEqualTo(Handshake.get(okhttp3.TlsVersion.TLS_1_2, suite,
                Collections.<Certificate>emptyList(), Collections.<Certificate>emptyList()));
        assertThat(handshake.hashCode()).isEqualTo(handshake.hashCode());
        SSLSession session = (SSLSession) Proxy.newProxyInstance(
                SSLSession.class.getClassLoader(), new Class<?>[] {SSLSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCipherSuite")) {
                        return suite.javaName();
                    }
                    if (method.getName().equals("getProtocol")) {
                        return "TLSv1.2";
                    }
                    if (method.getName().equals("getPeerCertificates")) {
                        throw new SSLPeerUnverifiedException("anonymous");
                    }
                    return null;
                });
        assertThat(Handshake.get(session).tlsVersion()).isEqualTo(okhttp3.TlsVersion.TLS_1_2);

        CertificatePinner pinner = new CertificatePinner.Builder()
                .add("pinned.example", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build();
        CertificatePinner equalPinner = new CertificatePinner.Builder()
                .add("pinned.example", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build();
        assertThat(pinner).isEqualTo(equalPinner);
        assertThat(pinner.hashCode()).isEqualTo(equalPinner.hashCode());
        pinner.check("other.example", Collections.<Certificate>emptyList());
        pinner.check("other.example", new Certificate[0]);
        try {
            CertificatePinner.pin(new java.security.cert.Certificate("X.509") {
                public byte[] getEncoded() {
                    return new byte[] {1};
                }

                public void verify(java.security.PublicKey key) {
                }

                public void verify(java.security.PublicKey key, String provider) {
                }

                public String toString() {
                    return "certificate";
                }

                public java.security.PublicKey getPublicKey() {
                    return null;
                }
            });
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("X509");
        }
    }

    private static final class TlsVersionShim {
        private static String name() {
            return okhttp3.TlsVersion.TLS_1_2.javaName();
        }
    }
}
