/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.sun.xml.ws.transport.httpspi.servlet.EndpointAdapter;
import com.sun.xml.ws.transport.httpspi.servlet.EndpointHttpContext;
import com.sun.xml.ws.transport.httpspi.servlet.Headers;
import jakarta.xml.ws.spi.http.HttpContext;
import org.junit.jupiter.api.Test;

public class Httpspi_servletTest {
    @Test
    void supportsCaseInsensitiveMultiValueHeaders() {
        Headers headers = new Headers();

        headers.add("x-request-ID", "first");
        headers.add("X-Request-id", "second");

        assertThat(headers).containsKey("X-REQUEST-ID");
        assertThat(headers.getFirst("x-request-id")).isEqualTo("first");
        assertThat(headers.get("X-Request-ID")).containsExactly("first", "second");

        headers.set("X-Request-ID", "replacement");
        assertThat(headers.get("x-request-id")).containsExactly("replacement");

        headers.put("Content-Type", List.of("text/plain"));
        assertThat(headers.entrySet()).hasSize(2);
        assertThat(headers.remove("CONTENT-TYPE")).containsExactly("text/plain");
    }

    @Test
    void preservesHeaderEntriesWithNullValues() {
        Headers headers = new Headers();

        headers.add("X-Optional", null);

        assertThat(headers).containsKey("x-optional");
        assertThat(headers.get("X-OPTIONAL")).containsExactly((String) null);
        assertThat(headers.getFirst("x-optional")).isNull();
    }

    @Test
    void appliesBulkHeaderChangesAndClearsAllEntries() {
        Headers headers = new Headers();

        headers.putAll(
                Map.of(
                        "accept", List.of("text/plain"),
                        "X-Trace", List.of("trace-id", "retry")));

        assertThat(headers).containsKeys("ACCEPT", "x-trace");
        assertThat(headers.containsValue(List.of("text/plain"))).isTrue();
        assertThat(headers.containsValue(List.of("missing"))).isFalse();

        headers.clear();

        assertThat(headers).isEmpty();
    }

    @Test
    void exposesNormalizedMapViewsAndValueEquality() {
        Headers headers = new Headers();
        headers.add("X-Trace", "trace-id");

        Headers equivalent = new Headers();
        equivalent.put("x-trace", List.of("trace-id"));

        assertThat(headers.size()).isEqualTo(1);
        assertThat(headers.keySet()).containsExactly("X-trace");
        assertThat(headers.values()).containsExactly(List.of("trace-id"));
        assertThat(headers).isEqualTo(equivalent);
        assertThat(headers.hashCode()).isEqualTo(equivalent.hashCode());
        assertThat(headers.toString()).contains("X-trace", "trace-id");
    }

    @Test
    void exposesEndpointContextAndValidUrlMapping() {
        EndpointAdapter adapter = new EndpointAdapter(null, "/echo/*");
        HttpContext context = adapter.getContext();

        assertThat(adapter.getEndpoint()).isNull();
        assertThat(adapter.getUrlPattern()).isEqualTo("/echo/*");
        assertThat(adapter.getValidPath()).isEqualTo("/echo");
        assertThat(context).isInstanceOf(EndpointHttpContext.class);
        assertThat(context.getPath()).isEqualTo("/echo/*");
        assertThat(context.getAttribute("missing")).isNull();
        assertThat(context.getAttributeNames()).isNull();

        EndpointAdapter exactMapping = new EndpointAdapter(null, "/exact");
        assertThat(exactMapping.getValidPath()).isEqualTo("/exact");
    }
}
