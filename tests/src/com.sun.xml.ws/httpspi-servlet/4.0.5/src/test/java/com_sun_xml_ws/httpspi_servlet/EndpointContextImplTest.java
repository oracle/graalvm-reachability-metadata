/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.sun.xml.ws.transport.httpspi.servlet.EndpointContextImpl;
import jakarta.xml.ws.Endpoint;
import org.junit.jupiter.api.Test;

public class EndpointContextImplTest {
    @Test
    void startsWithAnEmptyLiveEndpointSet() {
        EndpointContextImpl context = new EndpointContextImpl();

        Set<Endpoint> endpoints = context.getEndpoints();

        assertThat(endpoints).isEmpty();
        assertThat(context.getEndpoints()).isSameAs(endpoints);
    }
}
