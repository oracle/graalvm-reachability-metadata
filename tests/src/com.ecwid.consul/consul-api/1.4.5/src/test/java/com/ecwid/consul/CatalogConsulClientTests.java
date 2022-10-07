/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.CatalogConsulClient;
import com.ecwid.consul.v1.catalog.CatalogNodesRequest;
import com.ecwid.consul.v1.catalog.model.CatalogDeregistration;
import com.ecwid.consul.v1.catalog.model.CatalogRegistration;
import com.ecwid.consul.v1.catalog.model.Node;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ecwid.consul.utils.ConsulTestUtils.getFreePort;
import static com.ecwid.consul.utils.ConsulTestUtils.initTomcat;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogConsulClientTests {

    private Tomcat tomcat;
    private CatalogConsulClient consulClient;
    private byte[] requestBody;
    private String requestUri;
    private List<String> requestHeaderNames;

    @BeforeEach
    void setUp() {
        int port = getFreePort();
        try {
            tomcat = initTomcat(port, new TestServlet());
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        consulClient = new CatalogConsulClient("localhost", port);
    }

    @AfterEach
    void tearDownAll() {
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldRetrieveCatalogNodes() {
        CatalogNodesRequest request = CatalogNodesRequest.newBuilder().build();

        Response<List<Node>> response = consulClient.getCatalogNodes(request);

        assertThat(response).isNotNull();
        List<Node> nodes = response.getValue();
        assertThat(nodes).isNotEmpty();
        Node node = nodes.get(0);
        assertThat(node.getId()).isEqualTo("test");
        assertThat(requestUri).endsWith("/v1/catalog/nodes");
        assertThat(requestHeaderNames).contains("x-consul-token");
    }

    @Test
    void shouldRegisterCatalog() {
        CatalogRegistration catalogRegistration = new CatalogRegistration();
        CatalogRegistration.Service service = new CatalogRegistration.Service();
        service.setId("test");
        catalogRegistration.setService(service);

        Response<Void> response = consulClient.catalogRegister(catalogRegistration);

        assertThat(response).isNotNull();
        assertThat(requestUri).endsWith("/v1/catalog/register");
        assertThat(new String(requestBody)).isEqualTo("{\"Service\":{\"ID\":\"test\"},\"SkipNodeUpdate\":false}");
    }

    @Test
    void shouldDeregisterCatalog() {
        CatalogDeregistration catalogDeregistration = new CatalogDeregistration();
        catalogDeregistration.setServiceId("test");

        Response<Void> response = consulClient.catalogDeregister(catalogDeregistration);

        assertThat(response).isNotNull();
        assertThat(requestUri).endsWith("/v1/catalog/deregister");
        assertThat(new String(requestBody)).isEqualTo("{\"ServiceID\":\"test\"}");
    }

    private class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            requestUri = req.getRequestURI();
            requestHeaderNames = Collections.list(req.getHeaderNames());
            resp.setStatus(200);
            resp.setContentType("JSON/UTF-8");
            try (Writer writer = resp.getWriter()) {
                writer.write("[{\"ID\":\"test\"}]");
            }
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            requestUri = req.getRequestURI();
            requestBody = req.getInputStream().readAllBytes();
            resp.setStatus(200);
            resp.setContentType("JSON/UTF-8");
            try (Writer writer = resp.getWriter()) {
                writer.write("{\"ID\": \"test\"}");
            }
        }
    }
}
