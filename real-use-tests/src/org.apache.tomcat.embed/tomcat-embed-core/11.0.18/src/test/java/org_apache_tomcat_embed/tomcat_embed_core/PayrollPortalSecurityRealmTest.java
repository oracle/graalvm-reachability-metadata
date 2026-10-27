/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.realm.LockOutRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards a payroll portal with container-managed security, where `SecurityConstraint`s and a
 * BASIC `LoginConfig` are enforced by `BasicAuthenticator` against a `MemoryRealm` with salted
 * `MessageDigestCredentialHandler` passwords, wrapped in a `LockOutRealm`.
 *
 * A payroll team embeds its portal in a plain Java process and keeps authorization out of the
 * servlet: the operator lists users with hashed passwords and roles in a users file, and the
 * container decides who reaches which URL. Anonymous callers get `401` with the portal's BASIC
 * challenge, clerks read reports but get `403` when approving a payroll run, managers approve it,
 * and payslips demand a confidential transport, so plain HTTP is redirected to the HTTPS port.
 * The servlet sees the authenticated user, the `BASIC` auth type and the roles through the
 * standard request API, including a role reference it declares under its own name. A user who
 * keeps guessing a password is locked out even once they send the right one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayrollPortalSecurityRealmTest {

    private static final int HTTPS_PORT = 8443;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private Tomcat tomcat;

    private Path baseDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("payroll-tomcat");
        Path usersFile = writeUsersFile(baseDir);

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setRedirectPort(HTTPS_PORT);

        Context context = tomcat.addContext("/payroll", baseDir.toString());

        MessageDigestCredentialHandler credentials = credentialHandler();
        MemoryRealm users = new MemoryRealm();
        users.setPathname(usersFile.toString());
        users.setCredentialHandler(credentials);
        LockOutRealm realm = new LockOutRealm();
        realm.setFailureCount(3);
        realm.setLockOutTime(600);
        realm.addRealm(users);
        context.setRealm(realm);

        context.setLoginConfig(new LoginConfig("BASIC", "Payroll Portal", null, null));
        BasicAuthenticator authenticator = new BasicAuthenticator();
        authenticator.setCharset(StandardCharsets.UTF_8.name());
        context.getPipeline().addValve(authenticator);

        context.addSecurityRole("clerk");
        context.addSecurityRole("manager");
        context.addConstraint(constraint("reports", "/reports/*", "clerk", "manager"));
        context.addConstraint(constraint("approvals", "/approvals", "manager"));
        SecurityConstraint payslips = constraint("payslips", "/payslips/*", "clerk", "manager");
        payslips.setUserConstraint("CONFIDENTIAL");
        context.addConstraint(payslips);

        Wrapper wrapper = Tomcat.addServlet(context, "portal", new PortalServlet());
        wrapper.addSecurityReference("approver", "manager");
        context.addServletMappingDecoded("/reports/*", "portal");
        context.addServletMappingDecoded("/approvals", "portal");
        context.addServletMappingDecoded("/payslips/*", "portal");
        context.addServletMappingDecoded("/health", "portal");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/payroll";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void challengesAnonymousCallersWithTheBasicRealm() throws Exception {
        HttpResponse<String> response = send(get("/reports/monthly"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValue("Basic realm=\"Payroll Portal\", charset=UTF-8");
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        HttpResponse<String> response = send(get("/reports/monthly").header("Authorization", basic("ines", "guess")));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void letsAClerkReadReportsThroughTheStandardSecurityApi() throws Exception {
        HttpResponse<String> response = send(get("/reports/monthly")
                .header("Authorization", basic("ines", "clerk-pass-1")));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode caller = mapper.readTree(response.body());
        assertThat(caller.get("user").asText()).isEqualTo("ines");
        assertThat(caller.get("principal").asText()).isEqualTo("ines");
        assertThat(caller.get("authType").asText()).isEqualTo("BASIC");
        assertThat(caller.get("clerk").asBoolean()).isTrue();
        assertThat(caller.get("manager").asBoolean()).isFalse();
        assertThat(caller.get("approver").asBoolean()).isFalse();
        assertThat(caller.get("report").asText()).isEqualTo("/monthly");
    }

    @Test
    void forbidsAClerkFromApprovingAPayrollRun() throws Exception {
        HttpResponse<String> response = send(post("/approvals").header("Authorization", basic("ines", "clerk-pass-1")));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void letsAManagerApproveThroughTheDeclaredRoleReference() throws Exception {
        HttpResponse<String> response = send(post("/approvals")
                .header("Authorization", basic("marta", "manager-pass-2")));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode approval = mapper.readTree(response.body());
        assertThat(approval.get("approvedBy").asText()).isEqualTo("marta");
        assertThat(approval.get("approver").asBoolean()).isTrue();
        assertThat(approval.get("manager").asBoolean()).isTrue();
        assertThat(approval.get("clerk").asBoolean()).isFalse();
    }

    @Test
    void redirectsPayslipsToTheConfidentialPort() throws Exception {
        HttpResponse<String> response = send(get("/payslips/2026-09")
                .header("Authorization", basic("marta", "manager-pass-2")));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValue("https://localhost:" + HTTPS_PORT + "/payroll/payslips/2026-09");
    }

    @Test
    void servesTheUnconstrainedHealthCheckToAnyone() throws Exception {
        HttpResponse<String> response = send(get("/health"));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode health = mapper.readTree(response.body());
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.get("user").isNull()).isTrue();
    }

    @Test
    void locksOutAUserAfterRepeatedFailures() throws Exception {
        String goodPassword = basic("tomas", "temp-pass-3");
        assertThat(send(get("/reports/weekly").header("Authorization", goodPassword)).statusCode()).isEqualTo(200);

        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<String> failed = send(get("/reports/weekly")
                    .header("Authorization", basic("tomas", "wrong-" + attempt)));
            assertThat(failed.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> locked = send(get("/reports/weekly").header("Authorization", goodPassword));
        assertThat(locked.statusCode()).isEqualTo(401);
    }

    private static MessageDigestCredentialHandler credentialHandler() {
        MessageDigestCredentialHandler handler = new MessageDigestCredentialHandler();
        try {
            handler.setAlgorithm("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        handler.setIterations(1000);
        handler.setSaltLength(16);
        return handler;
    }

    /** Writes the operator's users file the way `digest.sh` output is pasted into it. */
    private static Path writeUsersFile(Path baseDir) throws IOException {
        MessageDigestCredentialHandler handler = credentialHandler();
        String usersXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <tomcat-users>
                  <role rolename="clerk"/>
                  <role rolename="manager"/>
                  <user username="ines" password="%s" roles="clerk"/>
                  <user username="marta" password="%s" roles="manager"/>
                  <user username="tomas" password="%s" roles="clerk"/>
                </tomcat-users>
                """.formatted(
                handler.mutate("clerk-pass-1"),
                handler.mutate("manager-pass-2"),
                handler.mutate("temp-pass-3"));
        Path conf = Files.createDirectories(baseDir.resolve("conf"));
        Path usersFile = conf.resolve("payroll-users.xml");
        Files.writeString(usersFile, usersXml, StandardCharsets.UTF_8);
        return usersFile;
    }

    private static SecurityConstraint constraint(String name, String pattern, String... roles) {
        SecurityCollection collection = new SecurityCollection();
        collection.setName(name);
        collection.addPatternDecoded(pattern);
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addCollection(collection);
        for (String role : roles) {
            constraint.addAuthRole(role);
        }
        return constraint;
    }

    private static String basic(String user, String password) {
        String token = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).POST(HttpRequest.BodyPublishers.noBody());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Reports what the container established about the caller; it never checks passwords itself. */
    static final class PortalServlet extends HttpServlet {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getServletPath().equals("/health")) {
                ObjectNode health = mapper.createObjectNode().put("status", "UP");
                health.put("user", request.getRemoteUser());
                writeJson(response, health);
                return;
            }
            Principal principal = request.getUserPrincipal();
            ObjectNode caller = roles(request)
                    .put("user", request.getRemoteUser())
                    .put("principal", principal.getName())
                    .put("authType", request.getAuthType())
                    .put("report", request.getPathInfo());
            writeJson(response, caller);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            writeJson(response, roles(request).put("approvedBy", request.getRemoteUser()));
        }

        private ObjectNode roles(HttpServletRequest request) {
            return mapper.createObjectNode()
                    .put("clerk", request.isUserInRole("clerk"))
                    .put("manager", request.isUserInRole("manager"))
                    .put("approver", request.isUserInRole("approver"));
        }

        private void writeJson(HttpServletResponse response, JsonNode body) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            mapper.writeValue(response.getOutputStream(), body);
        }
    }
}
