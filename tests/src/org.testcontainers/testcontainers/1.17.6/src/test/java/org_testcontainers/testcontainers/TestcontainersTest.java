/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
//package org_testcontainers.testcontainers;
//
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Disabled;
//import org.testcontainers.containers.GenericContainer;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Disabled("This test is pulling testcontainers/ryuk image version with many known vulnerabilities. It should be ignored until testcontainers change this image.")
//class TestcontainersTest {
//    private static final boolean DEBUG = false;
//
//    @BeforeAll
//    static void beforeAll() {
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", DEBUG ? "debug" : "warn");
//    }
//
//    @Test
//    void test() throws Exception {
//        try (GenericContainer<?> nginx = new GenericContainer<>("nginx:1-alpine-slim")) {
//            nginx.withExposedPorts(80).start();
//            HttpClient httpClient = HttpClient.newBuilder().build();
//            String url = String.format("http://%s:%d", nginx.getHost(), nginx.getFirstMappedPort());
//            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder().GET().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
//            assertThat(response.statusCode()).isEqualTo(200);
//            assertThat(response.body()).contains("<h1>Welcome to nginx!</h1>");
//        }
//    }
//}
