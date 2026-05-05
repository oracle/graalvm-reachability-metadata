/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.ProtocolHandshake;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class W3CHandshakeResponseTest {
    @Test
    void createsExceptionFromW3CHandshakeError() {
        HttpClient client = new ErrorHandshakeClient();
        Command command = new Command(null, DriverCommand.NEW_SESSION, Collections.emptyMap());

        WebDriverException thrown = assertThrows(
                WebDriverException.class,
                () -> new ProtocolHandshake().createSession(client, command));

        assertTrue(thrown.getMessage().contains("handshake failed"));
    }

    private static class ErrorHandshakeClient implements HttpClient {
        @Override
        public HttpResponse execute(HttpRequest request) throws IOException {
            HttpResponse response = new HttpResponse();
            response.setStatus(500);
            response.setContent("""
                    {"value":{"error":"session not created","message":"handshake failed","stacktrace":"server trace"}}
                    """.getBytes(StandardCharsets.UTF_8));
            return response;
        }
    }
}
