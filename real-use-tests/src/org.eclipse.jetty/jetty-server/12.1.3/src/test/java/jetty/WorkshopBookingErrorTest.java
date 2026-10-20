/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jetty's error handling subsystem to present workshop booking failures to browser and command-line clients.
public class WorkshopBookingErrorTest {

    @Test
    @Timeout(60)
    public void negotiatesBookingErrorsAndEscapesWorkshopNamesInBrowserPages() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        ErrorHandler errors = new ErrorHandler();
        errors.setShowStacks(false);
        errors.setShowCauses(false);
        errors.setShowOrigin(false);
        errors.setCacheControl("no-store");
        server.setErrorHandler(errors);
        server.setHandler(new WorkshopHandler());

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> browser = send(client, base.resolve("/workshops/wood-and-steel/bookings"),
                        "POST", "text/html");
                assertEquals(409, browser.statusCode());
                assertTrue(browser.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
                assertEquals("no-store", browser.headers().firstValue("Cache-Control").orElseThrow());
                assertTrue(browser.body().contains("HTTP ERROR 409"));
                assertTrue(browser.body().contains("Wood &amp; Steel"));
                assertTrue(browser.body().contains("is fully booked"));
                assertFalse(browser.body().contains("Wood & Steel"));

                HttpResponse<String> commandLine = send(client,
                        base.resolve("/workshops/wood-and-steel/bookings"), "POST", "text/plain");
                assertEquals(409, commandLine.statusCode());
                assertTrue(commandLine.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
                assertEquals("no-store", commandLine.headers().firstValue("Cache-Control").orElseThrow());
                assertTrue(commandLine.body().contains("HTTP ERROR 409"));
                assertTrue(commandLine.body().contains("Workshop \"Wood & Steel\" is fully booked"));
                assertFalse(commandLine.body().contains("<html"));

                HttpResponse<String> missing = send(client, base.resolve("/workshops/unknown"), "GET", "text/plain");
                assertEquals(404, missing.statusCode());
                assertTrue(missing.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
                assertTrue(missing.body().contains("HTTP ERROR 404"));
                assertEquals("no-store", missing.headers().firstValue("Cache-Control").orElseThrow());

                HttpResponse<String> schedule = send(client, base.resolve("/workshops"), "GET", "text/plain");
                assertEquals(200, schedule.statusCode());
                assertEquals("Wood & Steel: fully booked\nWatercolors: 4 seats available\n", schedule.body());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> send(HttpClient client, URI uri, String method, String accept)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", accept)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class WorkshopHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            if ("POST".equals(request.getMethod()) && "/workshops/wood-and-steel/bookings".equals(path)) {
                Response.writeError(request, response, callback, 409, "Workshop \"Wood & Steel\" is fully booked");
                return true;
            }
            if ("GET".equals(request.getMethod()) && "/workshops".equals(path)) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                Content.Sink.write(response, true, """
                        Wood & Steel: fully booked
                        Watercolors: 4 seats available
                        """, callback);
                return true;
            }
            return false;
        }
    }
}
