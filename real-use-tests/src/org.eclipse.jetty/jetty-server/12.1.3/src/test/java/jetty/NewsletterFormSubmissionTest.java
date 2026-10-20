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
import org.eclipse.jetty.server.FormFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's URL-encoded form processing subsystem for newsletter subscription submissions.
public class NewsletterFormSubmissionTest {

    @Test
    @Timeout(60)
    public void decodesSubscriptionFieldsAndRejectsIncompleteSubmissions() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new SubscriptionHandler());

        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/subscribe");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> subscribed = submit(client, endpoint,
                        "name=Ren%C3%A9e+Smith&email=renee%2Bnews%40example.com"
                                + "&topic=books&topic=arts+%26+crafts&note=discount%3D10%25");
                assertEquals(201, subscribed.statusCode());
                assertEquals("text/plain;charset=utf-8",
                        subscribed.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("""
                        Subscriber: Ren\u00e9e Smith
                        Email: renee+news@example.com
                        Topics: books, arts & crafts
                        Note: discount=10%
                        """, subscribed.body());

                HttpResponse<String> missingEmail = submit(client,
                        URI.create(endpoint + "?email=query-only%40example.com"),
                        "name=Taylor&topic=books");
                assertEquals(422, missingEmail.statusCode());
                assertEquals("Name and email are required\n", missingEmail.body());

                HttpResponse<String> blankName = submit(client, endpoint,
                        "name=+&email=taylor%40example.com");
                assertEquals(422, blankName.statusCode());
                assertEquals("Name and email are required\n", blankName.body());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> submit(HttpClient client, URI endpoint, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class SubscriptionHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/subscribe".equals(Request.getPathInContext(request)) || !"POST".equals(request.getMethod())) {
                return false;
            }
            FormFields.onFields(request, Promise.Invocable.from(getInvocationType(), fields -> {
                String name = fields.getValue("name");
                String email = fields.getValue("email");
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                if (name == null || name.isBlank() || email == null || email.isBlank()) {
                    response.setStatus(422);
                    Content.Sink.write(response, true, "Name and email are required\n", callback);
                    return;
                }
                response.setStatus(201);
                String confirmation = """
                        Subscriber: %s
                        Email: %s
                        Topics: %s
                        Note: %s
                        """.formatted(name, email, String.join(", ", fields.getValuesOrEmpty("topic")),
                                fields.getValue("note"));
                Content.Sink.write(response, true, confirmation, callback);
            }, callback::failed));
            return true;
        }
    }
}
