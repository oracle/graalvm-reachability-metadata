/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the domain name mapping subsystem of netty-common: `DomainWildcardMappingBuilder`
 * building immutable `Mapping` instances with exact and single-level wildcard hostnames, the
 * default value for unmatched or absent names, case and IDN normalization of hostnames, and the
 * validation of malformed wildcard patterns.
 *
 * A TLS-terminating edge proxy picks a certificate profile from the SNI hostname a client sends.
 * Operators register exact hostnames and wildcard certificates; the proxy routes each handshake to
 * the matching profile and falls back to a default certificate for unknown or missing names.
 */
class VirtualHostCertificateDomainMappingTest {

    @Test
    void sniHostnamesSelectExactAndWildcardCertificates() {
        CertificateSelector selector = new CertificateSelector(new CertificateProfile("default", "CN=edge.invalid"))
                .register("api.example.com", new CertificateProfile("api", "CN=api.example.com"))
                .register("*.example.com", new CertificateProfile("example-wildcard", "CN=*.example.com"))
                .register("*.eu.shop.example.org", new CertificateProfile("shop-eu", "CN=*.eu.shop.example.org"))
                .register("shop.example.org", new CertificateProfile("shop", "CN=shop.example.org"));

        Map<String, String> routed = selector.handshake(
                "api.example.com",
                "www.example.com",
                "example.com",
                "a.b.example.com",
                "berlin.eu.shop.example.org",
                "shop.example.org",
                "unknown.test",
                null);

        assertThat(routed).containsExactly(
                Map.entry("api.example.com", "api"),
                Map.entry("www.example.com", "example-wildcard"),
                Map.entry("example.com", "default"),
                Map.entry("a.b.example.com", "default"),
                Map.entry("berlin.eu.shop.example.org", "shop-eu"),
                Map.entry("shop.example.org", "shop"),
                Map.entry("unknown.test", "default"),
                Map.entry("<no-sni>", "default"));
        assertThat(selector.handshakeLog()).containsExactly(
                "api.example.com -> CN=api.example.com",
                "www.example.com -> CN=*.example.com",
                "example.com -> CN=edge.invalid",
                "a.b.example.com -> CN=edge.invalid",
                "berlin.eu.shop.example.org -> CN=*.eu.shop.example.org",
                "shop.example.org -> CN=shop.example.org",
                "unknown.test -> CN=edge.invalid",
                "<no-sni> -> CN=edge.invalid");
    }

    @Test
    void hostnamesAreNormalizedForCaseAndInternationalizedNames() {
        Mapping<String, String> backends = new DomainWildcardMappingBuilder<String>(4, "fallback-pool")
                .add("Static.CDN.Example.NET", "static-pool")
                .add("*.B\u00fccher.example", "books-pool")
                .add("xn--caf-dma.example", "cafe-pool")
                .build();

        assertThat(backends.map("static.cdn.example.net")).isEqualTo("static-pool");
        assertThat(backends.map("STATIC.cdn.EXAMPLE.net")).isEqualTo("static-pool");
        assertThat(backends.map("shop.b\u00fccher.example")).isEqualTo("books-pool");
        assertThat(backends.map("shop.xn--bcher-kva.example")).isEqualTo("books-pool");
        assertThat(backends.map("caf\u00e9.example")).isEqualTo("cafe-pool");
        assertThat(backends.map("CAF\u00c9.example")).isEqualTo("cafe-pool");
        assertThat(backends.map("cdn.example.net")).isEqualTo("fallback-pool");
    }

    @Test
    void laterRegistrationsReplaceEarlierOnesAndBuiltMappingsStayImmutable() {
        DomainWildcardMappingBuilder<String> builder = new DomainWildcardMappingBuilder<>("default")
                .add("*.tenant.example", "tenant-v1")
                .add("admin.tenant.example", "admin");
        Mapping<String, String> firstRelease = builder.build();

        builder.add("*.tenant.example", "tenant-v2").add("billing.tenant.example", "billing");
        Mapping<String, String> secondRelease = builder.build();

        assertThat(firstRelease.map("acme.tenant.example")).isEqualTo("tenant-v1");
        assertThat(firstRelease.map("billing.tenant.example")).isEqualTo("tenant-v1");
        assertThat(firstRelease.map("admin.tenant.example")).isEqualTo("admin");
        assertThat(secondRelease.map("acme.tenant.example")).isEqualTo("tenant-v2");
        assertThat(secondRelease.map("billing.tenant.example")).isEqualTo("billing");
        assertThat(secondRelease.map("admin.tenant.example")).isEqualTo("admin");
        assertThat(secondRelease.map("tenant.example")).isEqualTo("default");
        assertThat(secondRelease.toString()).contains("default").contains("tenant-v2").contains("billing");
    }

    @Test
    void malformedRegistrationsAreRejected() {
        DomainWildcardMappingBuilder<String> builder = new DomainWildcardMappingBuilder<>("default");

        assertThatThrownBy(() -> builder.add("*example.com", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.add("*.", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.add(".example.com", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.add("", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.add(null, "bad"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.add("ok.example.com", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DomainWildcardMappingBuilder<String>(null))
                .isInstanceOf(NullPointerException.class);

        Mapping<String, String> mapping = builder.add("ok.example.com", "ok").build();
        assertThat(mapping.map("ok.example.com")).isEqualTo("ok");
        assertThat(mapping.map("example.com")).isEqualTo("default");
    }

    /// The certificate an edge proxy presents for a matched hostname.
    private record CertificateProfile(String name, String subject) {
    }

    /// Picks a certificate profile from the SNI hostname of each incoming TLS handshake.
    private static final class CertificateSelector {

        private final DomainWildcardMappingBuilder<CertificateProfile> builder;
        private final List<String> handshakeLog = new ArrayList<>();
        private Mapping<String, CertificateProfile> mapping;

        CertificateSelector(CertificateProfile fallback) {
            this.builder = new DomainWildcardMappingBuilder<>(fallback);
        }

        CertificateSelector register(String hostname, CertificateProfile profile) {
            builder.add(hostname, profile);
            mapping = null;
            return this;
        }

        Map<String, String> handshake(String... sniHostnames) {
            if (mapping == null) {
                mapping = builder.build();
            }
            Map<String, String> routed = new LinkedHashMap<>();
            for (String sni : sniHostnames) {
                CertificateProfile profile = mapping.map(sni);
                String label = sni == null ? "<no-sni>" : sni;
                routed.put(label, profile.name());
                handshakeLog.add(label + " -> " + profile.subject());
            }
            return routed;
        }

        List<String> handshakeLog() {
            return handshakeLog;
        }
    }
}
