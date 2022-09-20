/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul;

import java.io.IOException;
import java.io.Writer;

import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.acl.AclConsulClient;
import com.ecwid.consul.v1.acl.model.Acl;
import com.ecwid.consul.v1.acl.model.AclType;
import com.ecwid.consul.v1.acl.model.NewAcl;
import com.ecwid.consul.v1.acl.model.UpdateAcl;
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

class AclConsulClientTests {

    private static final String MASTER_TOKEN = "mastertoken";
    private Tomcat tomcat;
    private AclConsulClient consulClient;

    private byte[] requestBody;
    private String requestUri;

    @BeforeEach
    void setUp() {
        int port = getFreePort();
        try {
            tomcat = initTomcat(port, new TestServlet());
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        consulClient = new AclConsulClient("localhost", port);
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
    void shouldCreateAclToken() {
        NewAcl newAcl = new NewAcl();
        newAcl.setName("test-acl");
        newAcl.setType(AclType.CLIENT);
        newAcl.setRules("");

        Response<String> response = consulClient.aclCreate(newAcl, MASTER_TOKEN);

        assertThat(response).isNotNull();
        assertThat(response.getValue()).isEqualTo("test");
        assertThat(requestUri).endsWith("/v1/acl/create");
        assertThat(new String(requestBody)).isEqualTo("{\"Name\":\"test-acl\",\"Type\":\"client\",\"Rules\":\"\"}");
    }

    @Test
    void shouldRetrieveAclToken() {
        Response<Acl> response = consulClient.getAcl("test");

        assertThat(response).isNotNull();
        Acl acl = response.getValue();
        assertThat(acl.getCreateIndex()).isEqualTo(0);
        assertThat(acl.getModifyIndex()).isEqualTo(0);
        assertThat(acl.getName()).isEqualTo("test-acl");
        assertThat(acl.getType()).isEqualTo(AclType.CLIENT);
        assertThat(acl.getRules()).isEqualTo("");
        assertThat(requestUri).endsWith("/v1/acl/info/test");
    }

    @Test
    void shouldUpdateAclToken() {
        UpdateAcl updateAcl = new UpdateAcl();
        updateAcl.setName("test-acl");
        updateAcl.setType(AclType.MANAGEMENT);
        updateAcl.setRules("");

        Response<Void> response = consulClient.aclUpdate(updateAcl, MASTER_TOKEN);

        assertThat(response).isNotNull();
        assertThat(requestUri).endsWith("/v1/acl/update");
        assertThat(new String(requestBody)).isEqualTo("{\"Name\":\"test-acl\",\"Type\":\"management\",\"Rules\":\"\"}");
    }


    private class TestServlet extends HttpServlet {

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

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            requestUri = req.getRequestURI();
            resp.setStatus(200);
            resp.setContentType("JSON/UTF-8");
            try (Writer writer = resp.getWriter()) {
                writer.write("[{\"Name\":\"test-acl\",\"Type\":\"client\",\"Rules\":\"\"}]");
            }
        }
    }
}
