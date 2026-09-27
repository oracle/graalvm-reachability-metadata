/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.xml.ws.transport.httpspi.servlet.EndpointAdapter;
import org.junit.jupiter.api.Test;

public class EndpointAdapterTest {
    @Test
    void publishesAndDisposesEndpointUsingServletContext() {
        RecordingEndpoint endpoint = new RecordingEndpoint();
        EndpointAdapter adapter = new EndpointAdapter(endpoint, "/services/greeting/*");

        assertThat(adapter.getValidPath()).isEqualTo("/services/greeting");
        assertThat(endpoint.isPublished()).isFalse();

        adapter.publish();

        assertThat(endpoint.publishedContext).isSameAs(adapter.getContext());
        assertThat(endpoint.isPublished()).isTrue();

        adapter.dispose();

        assertThat(endpoint.isPublished()).isFalse();
    }
}
