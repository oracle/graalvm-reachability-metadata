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
import java.util.Random;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
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

class KeyValueConsulClientTests {

    private Tomcat tomcat;
    private ConsulClient consulClient;

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
        consulClient = new ConsulClient("localhost", port);
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
    void shouldSetKVValue() {
        final String testKey = "test_key";
        final byte[] testValue = new byte[100];
        new Random().nextBytes(testValue);

        Response<Boolean> response = consulClient.setKVBinaryValue(testKey, testValue);

        assertThat(response.getValue()).isTrue();
        assertThat(testValue).isEqualTo(requestBody);
        assertThat(requestUri).endsWith(testKey);
        assertThat(requestHeaderNames).contains("x-consul-token");
    }

    @Test
    void shouldDeleteKVValue() {
        final String testKey = "test_key";

        Response<Void> response = consulClient.deleteKVValue(testKey);

        assertThat(response).isNotNull();
        assertThat(requestUri).endsWith(testKey);
        assertThat(requestHeaderNames).contains("x-consul-token");
    }

    private class TestServlet extends HttpServlet {

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            requestUri = req.getRequestURI();
            requestBody = req.getInputStream().readAllBytes();
            requestHeaderNames = Collections.list(req.getHeaderNames());
            resp.setStatus(200);
            resp.setContentType("JSON/UTF-8");
            try (Writer writer = resp.getWriter()) {
                writer.write("true");
            }
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            requestUri = req.getRequestURI();
            requestHeaderNames = Collections.list(req.getHeaderNames());
            resp.setStatus(200);
        }
    }
}
