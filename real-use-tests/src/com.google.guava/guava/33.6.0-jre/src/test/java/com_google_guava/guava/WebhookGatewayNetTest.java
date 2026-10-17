/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.HostAndPort;
import com.google.common.net.HostSpecifier;
import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.google.common.net.MediaType;
import com.google.common.net.PercentEscaper;
import com.google.common.net.UrlEscapers;
import com.google.common.xml.XmlEscapers;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

/**
 * Network value types and escaping with `com.google.common.net`, `com.google.common.escape`,
 * `com.google.common.html`, and `com.google.common.xml`: `InternetDomainName` public-suffix
 * analysis, `MediaType` parsing and negotiation, `InetAddresses`, `HostAndPort`,
 * `HostSpecifier`, `UrlEscapers`, `PercentEscaper`, HTML and XML escapers, and custom
 * `Escapers`.
 *
 * A webhook gateway accepts subscriber registrations, groups callback hosts by the registrable
 * domain that owns them, negotiates payload content types, filters client addresses against an
 * allow list, builds signed callback URLs, and renders delivery reports as HTML, XML, and CSV.
 */
public class WebhookGatewayNetTest {

    @Test
    void groupsCallbackHostsByTheRegistrableDomainThatOwnsThem() {
        InternetDomainName api = InternetDomainName.from("Hooks.API.Example.co.uk");

        assertEquals("hooks.api.example.co.uk", api.toString());
        assertEquals(ImmutableList.of("hooks", "api", "example", "co", "uk"), api.parts());
        assertTrue(api.hasPublicSuffix());
        assertEquals(InternetDomainName.from("co.uk"), api.publicSuffix());
        assertTrue(api.isUnderPublicSuffix());
        assertFalse(api.isTopPrivateDomain());
        assertEquals(InternetDomainName.from("example.co.uk"), api.topPrivateDomain());
        assertEquals(InternetDomainName.from("api.example.co.uk"), api.parent());
        assertEquals(InternetDomainName.from("v2.hooks.api.example.co.uk"), api.child("v2"));
        assertTrue(InternetDomainName.from("co.uk").isPublicSuffix());

        InternetDomainName blog = InternetDomainName.from("team.blogspot.com");
        assertEquals(InternetDomainName.from("blogspot.com"), blog.publicSuffix());
        assertEquals(InternetDomainName.from("team.blogspot.com"), blog.topPrivateDomain());
        assertEquals(InternetDomainName.from("com"), blog.registrySuffix());
        assertEquals(InternetDomainName.from("blogspot.com"), blog.topDomainUnderRegistrySuffix());

        InternetDomainName intranet = InternetDomainName.from("localhost");
        assertFalse(intranet.hasPublicSuffix());
        assertThrows(IllegalStateException.class, intranet::topPrivateDomain);

        assertFalse(InternetDomainName.isValid("-hooks.example.com"));
        assertFalse(InternetDomainName.isValid("hooks..example.com"));
        assertThrows(IllegalArgumentException.class, () -> InternetDomainName.from("bad host"));

        SubscriberRegistry registry = new SubscriberRegistry();
        registry.register("https://hooks.shop.example.com/orders");
        registry.register("https://billing.shop.example.com/invoices");
        registry.register("https://alerts.partner.co.uk/notify");
        registry.register("https://ci.team.github.io/build");
        registry.register("https://hooks.shop.example.com:8443/refunds");

        ImmutableListMultimap<String, String> byOwner = registry.callbacksByOwner();
        assertEquals(ImmutableList.of("example.com", "partner.co.uk", "team.github.io"),
                ImmutableList.copyOf(byOwner.keySet()));
        assertEquals(ImmutableList.of("hooks.shop.example.com:443", "billing.shop.example.com:443",
                "hooks.shop.example.com:8443"), byOwner.get("example.com"));
        assertEquals(ImmutableList.of("ci.team.github.io:443"), byOwner.get("team.github.io"));
    }

    @Test
    void negotiatesPayloadContentTypes() {
        MediaType declared = MediaType.parse("Application/JSON; Charset=\"UTF-8\"");

        assertEquals("application", declared.type());
        assertEquals("json", declared.subtype());
        assertEquals(StandardCharsets.UTF_8, declared.charset().get());
        assertEquals(MediaType.JSON_UTF_8, declared);
        assertTrue(declared.is(MediaType.JSON_UTF_8.withoutParameters()));
        assertTrue(declared.is(MediaType.ANY_APPLICATION_TYPE));
        assertFalse(declared.is(MediaType.ANY_TEXT_TYPE));
        assertFalse(MediaType.JSON_UTF_8.withoutParameters().is(declared));

        MediaType latin = MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1);
        assertEquals("text/plain; charset=iso-8859-1", latin.toString());
        assertEquals(StandardCharsets.ISO_8859_1, latin.charset().get());

        MediaType multipart = MediaType.parse("multipart/form-data; boundary=\"batch 42\"");
        assertEquals(ImmutableList.of("batch 42"), multipart.parameters().get("boundary"));
        assertEquals("multipart/form-data; boundary=\"batch 42\"", multipart.toString());
        assertFalse(multipart.withoutParameters().charset().isPresent());

        MediaType tagged = MediaType.create("application", "vnd.gateway.event+json")
                .withParameter("version", "3");
        assertEquals("application/vnd.gateway.event+json; version=3", tagged.toString());
        assertEquals(tagged, MediaType.parse(tagged.toString()));

        assertThrows(IllegalArgumentException.class, () -> MediaType.parse("json"));
        assertThrows(UnsupportedCharsetException.class,
                () -> MediaType.parse("text/plain; charset=no-such-charset").charset());

        ContentNegotiator negotiator = new ContentNegotiator(ImmutableList.of(
                MediaType.JSON_UTF_8, MediaType.APPLICATION_XML_UTF_8, MediaType.PLAIN_TEXT_UTF_8));
        assertEquals(MediaType.APPLICATION_XML_UTF_8, negotiator.choose("application/xml, text/*"));
        assertEquals(MediaType.PLAIN_TEXT_UTF_8, negotiator.choose("image/png, text/*"));
        assertEquals(MediaType.JSON_UTF_8, negotiator.choose("*/*"));
        assertThrows(IllegalArgumentException.class, () -> negotiator.choose("image/png"));
        assertEquals("Content-Type", HttpHeaders.CONTENT_TYPE);
        assertEquals("Accept", HttpHeaders.ACCEPT);
    }

    @Test
    void filtersClientAddressesAgainstAnAllowList() {
        InetAddress office = InetAddresses.forString("192.168.10.20");
        assertInstanceOf(Inet4Address.class, office);
        assertEquals("192.168.10.20", InetAddresses.toAddrString(office));
        assertEquals(0xC0A80A14, InetAddresses.coerceToInteger(office));
        assertEquals(InetAddresses.forString("192.168.10.21"), InetAddresses.increment(office));
        assertEquals(InetAddresses.forString("192.168.10.19"), InetAddresses.decrement(office));
        assertEquals(office, InetAddresses.fromInteger(0xC0A80A14));

        InetAddress datacenter = InetAddresses.forString("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertInstanceOf(Inet6Address.class, datacenter);
        assertEquals("2001:db8::1", InetAddresses.toAddrString(datacenter));
        assertEquals("[2001:db8::1]", InetAddresses.toUriString(datacenter));
        assertEquals(datacenter, InetAddresses.forUriString("[2001:db8::1]"));

        assertTrue(InetAddresses.isInetAddress("10.0.0.1"));
        assertFalse(InetAddresses.isInetAddress("256.0.0.1"));
        assertFalse(InetAddresses.isInetAddress("hooks.example.com"));
        assertTrue(InetAddresses.isMappedIPv4Address("::ffff:192.168.10.20"));
        assertThrows(IllegalArgumentException.class, () -> InetAddresses.forString("10.0.0"));

        AddressAllowList allowList = new AddressAllowList();
        allowList.allowRange("10.20.0.0", "10.20.0.255");
        allowList.allowRange("2001:db8::1", "2001:db8::ff");
        assertTrue(allowList.permits("10.20.0.7"));
        assertTrue(allowList.permits("2001:db8::a0"));
        assertFalse(allowList.permits("10.20.1.0"));
        assertFalse(allowList.permits("2001:db8::100"));
        assertFalse(allowList.permits("not-an-address"));
    }

    @Test
    void parsesSubscriberEndpoints() throws ParseException {
        HostAndPort ipv6 = HostAndPort.fromString("[2001:db8::7]:8443");
        assertEquals("2001:db8::7", ipv6.getHost());
        assertEquals(8443, ipv6.getPort());
        assertEquals("[2001:db8::7]:8443", ipv6.toString());

        HostAndPort bare = HostAndPort.fromString("hooks.example.com").withDefaultPort(443);
        assertEquals(443, bare.getPort());
        assertEquals("hooks.example.com:443", bare.toString());
        assertFalse(HostAndPort.fromString("hooks.example.com").hasPort());
        assertEquals(80, HostAndPort.fromString("hooks.example.com").getPortOrDefault(80));
        assertEquals(HostAndPort.fromParts("10.0.0.5", 9000), HostAndPort.fromString("10.0.0.5:9000"));
        assertThrows(IllegalArgumentException.class,
                () -> HostAndPort.fromString("2001:db8::7").requireBracketsForIPv6());
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.fromString("host:99999"));

        assertTrue(HostSpecifier.isValid("hooks.example.com"));
        assertTrue(HostSpecifier.isValid("[2001:db8::7]"));
        assertFalse(HostSpecifier.isValid("hooks example com"));
        assertEquals("hooks.example.com", HostSpecifier.from("HOOKS.example.com").toString());
        assertEquals("[2001:db8::7]", HostSpecifier.fromValid("2001:db8::7").toString());
    }

    @Test
    void buildsSignedCallbackUrls() {
        Escaper pathSegment = UrlEscapers.urlPathSegmentEscaper();
        Escaper formParameter = UrlEscapers.urlFormParameterEscaper();
        Escaper fragment = UrlEscapers.urlFragmentEscaper();

        assertEquals("reports%2F2024%20Q1", pathSegment.escape("reports/2024 Q1"));
        assertEquals("a+b%26c%3Dd%2F%C3%A9", formParameter.escape("a b&c=d/\u00e9"));
        assertEquals("step%201/a?b", fragment.escape("step 1/a?b"));
        assertEquals("caf%C3%A9%20menu", new PercentEscaper("-_.", false).escape("caf\u00e9 menu"));
        assertEquals("caf%C3%A9+menu", new PercentEscaper("-_.", true).escape("caf\u00e9 menu"));

        Map<String, String> query = new LinkedHashMap<>();
        query.put("event", "order.paid");
        query.put("customer", "Zo\u00eb & Co");
        query.put("redirect", "https://shop.example.com/thanks?id=7");
        CallbackUrlBuilder builder = new CallbackUrlBuilder(HostAndPort.fromString("hooks.example.com:8443"));
        assertEquals("https://hooks.example.com:8443/tenants/acme%20corp/events%2Forders"
                + "?event=order.paid&customer=Zo%C3%AB+%26+Co"
                + "&redirect=https%3A%2F%2Fshop.example.com%2Fthanks%3Fid%3D7#delivery%201",
                builder.build(ImmutableList.of("tenants", "acme corp", "events/orders"), query, "delivery 1"));
    }

    @Test
    void rendersDeliveryReportsInSeveralFormats() {
        String subscriber = "<Tom & Jerry's \"Shop\">";

        assertEquals("&lt;Tom &amp; Jerry&#39;s &quot;Shop&quot;&gt;",
                HtmlEscapers.htmlEscaper().escape(subscriber));
        assertEquals("&lt;Tom &amp; Jerry's \"Shop\"&gt;",
                XmlEscapers.xmlContentEscaper().escape(subscriber));
        assertEquals("&lt;Tom &amp; Jerry&apos;s &quot;Shop&quot;&gt;&#xA;",
                XmlEscapers.xmlAttributeEscaper().escape(subscriber + "\n"));

        Escaper csv = Escapers.builder()
                .addEscape('\\', "\\\\")
                .addEscape(',', "\\,")
                .addEscape('\n', "\\n")
                .build();
        assertEquals("a\\,b\\\\c\\nd", csv.escape("a,b\\c\nd"));
        assertEquals("plain", csv.escape("plain"));

        Escaper ascii = Escapers.builder()
                .setSafeRange(' ', '~')
                .setUnsafeReplacement("?")
                .build();
        assertEquals("Zo? ?", ascii.escape("Zo\u00eb \u2603"));

        DeliveryReport report = new DeliveryReport(subscriber, ImmutableList.of("ok", "retry, 5xx"));
        assertEquals("<li>&lt;Tom &amp; Jerry&#39;s &quot;Shop&quot;&gt;: ok</li>"
                + "<li>&lt;Tom &amp; Jerry&#39;s &quot;Shop&quot;&gt;: retry, 5xx</li>", report.html());
        assertEquals("<report subscriber=\"&lt;Tom &amp; Jerry&apos;s &quot;Shop&quot;&gt;\">"
                + "<status>ok</status><status>retry, 5xx</status></report>", report.xml());
        assertEquals("<Tom & Jerry's \"Shop\">,ok\n<Tom & Jerry's \"Shop\">,retry\\, 5xx",
                report.csv(csv));
    }

    /// Records callback URLs and groups their endpoints under the registrable owning domain.
    private static final class SubscriberRegistry {
        private final ImmutableListMultimap.Builder<String, String> callbacks =
                ImmutableListMultimap.builder();

        void register(String callbackUrl) {
            String authority = callbackUrl.substring("https://".length(), callbackUrl.indexOf('/', 8));
            HostAndPort endpoint = HostAndPort.fromString(authority).withDefaultPort(443);
            InternetDomainName domain = InternetDomainName.from(endpoint.getHost());
            callbacks.put(domain.topPrivateDomain().toString(), endpoint.toString());
        }

        ImmutableListMultimap<String, String> callbacksByOwner() {
            return callbacks.build();
        }
    }

    /// Picks the first supported media type that a client's `Accept` header admits.
    private static final class ContentNegotiator {
        private final ImmutableList<MediaType> supported;

        ContentNegotiator(ImmutableList<MediaType> supported) {
            this.supported = supported;
        }

        MediaType choose(String acceptHeader) {
            for (String range : acceptHeader.split(",")) {
                MediaType accepted = MediaType.parse(range.trim());
                for (MediaType candidate : supported) {
                    if (candidate.is(accepted)) {
                        return candidate;
                    }
                }
            }
            throw new IllegalArgumentException("Not acceptable: " + acceptHeader);
        }
    }

    /// Admits client addresses that fall inside an inclusive address range.
    private static final class AddressAllowList {
        private final ImmutableList.Builder<InetAddress[]> ranges = ImmutableList.builder();

        void allowRange(String first, String last) {
            ranges.add(new InetAddress[] {InetAddresses.forString(first), InetAddresses.forString(last)});
        }

        boolean permits(String client) {
            if (!InetAddresses.isInetAddress(client)) {
                return false;
            }
            InetAddress address = InetAddresses.forString(client);
            for (InetAddress[] range : ranges.build()) {
                if (range[0].getClass() == address.getClass()
                        && InetAddresses.toBigInteger(range[0]).compareTo(InetAddresses.toBigInteger(address)) <= 0
                        && InetAddresses.toBigInteger(address).compareTo(InetAddresses.toBigInteger(range[1])) <= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /// Builds an HTTPS callback URL from escaped path segments, query parameters, and a fragment.
    private static final class CallbackUrlBuilder {
        private final HostAndPort endpoint;

        CallbackUrlBuilder(HostAndPort endpoint) {
            this.endpoint = endpoint;
        }

        String build(ImmutableList<String> segments, Map<String, String> query, String fragment) {
            StringBuilder url = new StringBuilder("https://").append(endpoint);
            for (String segment : segments) {
                url.append('/').append(UrlEscapers.urlPathSegmentEscaper().escape(segment));
            }
            StringJoiner parameters = new StringJoiner("&", "?", "");
            Escaper escaper = UrlEscapers.urlFormParameterEscaper();
            query.forEach((name, value) -> parameters.add(escaper.escape(name) + "=" + escaper.escape(value)));
            return url.append(parameters).append('#')
                    .append(UrlEscapers.urlFragmentEscaper().escape(fragment)).toString();
        }
    }

    /// Renders one subscriber's delivery outcomes for the dashboard, the XML feed, and the CSV export.
    private record DeliveryReport(String subscriber, ImmutableList<String> statuses) {

        String html() {
            Escaper escaper = HtmlEscapers.htmlEscaper();
            StringBuilder html = new StringBuilder();
            for (String status : statuses) {
                html.append("<li>").append(escaper.escape(subscriber)).append(": ")
                        .append(escaper.escape(status)).append("</li>");
            }
            return html.toString();
        }

        String xml() {
            StringBuilder xml = new StringBuilder("<report subscriber=\"")
                    .append(XmlEscapers.xmlAttributeEscaper().escape(subscriber)).append("\">");
            for (String status : statuses) {
                xml.append("<status>").append(XmlEscapers.xmlContentEscaper().escape(status)).append("</status>");
            }
            return xml.append("</report>").toString();
        }

        String csv(Escaper escaper) {
            StringJoiner rows = new StringJoiner("\n");
            for (String status : statuses) {
                rows.add(subscriber + "," + escaper.escape(status));
            }
            return rows.toString();
        }
    }
}
