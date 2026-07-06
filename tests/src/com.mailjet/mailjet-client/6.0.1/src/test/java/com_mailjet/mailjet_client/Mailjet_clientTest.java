/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mailjet.mailjet_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.Resource;
import com.mailjet.client.enums.ApiAuthenticationType;
import com.mailjet.client.enums.ApiVersion;
import com.mailjet.client.resource.Contact;
import com.mailjet.client.resource.Contactslist;
import com.mailjet.client.resource.Csvimport;
import com.mailjet.client.transactional.Attachment;
import com.mailjet.client.transactional.SendContact;
import com.mailjet.client.transactional.SendEmailsRequest;
import com.mailjet.client.transactional.TrackClicks;
import com.mailjet.client.transactional.TrackOpens;
import com.mailjet.client.transactional.TransactionalEmail;
import com.mailjet.client.transactional.response.EmailResult;
import com.mailjet.client.transactional.response.MessageResult;
import com.mailjet.client.transactional.response.SendEmailError;
import com.mailjet.client.transactional.response.SendEmailsResponse;
import com.mailjet.client.transactional.response.SentMessageStatus;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class Mailjet_clientTest {
    @Test
    void requestBuildsUrlsQueriesAndJsonBodies() throws Exception {
        MailjetRequest contactRequest = new MailjetRequest(Contact.resource, "user+tag@example.com")
                .filter(Contact.LIMIT, 25)
                .filter(Contact.COUNTONLY, "false")
                .property(Contact.EMAIL, "user+tag@example.com")
                .append("Tags", "native")
                .append("Tags", "mailjet");

        String url = contactRequest.buildUrl("https://api.test.local/base");
        assertThat(url).startsWith("https://api.test.local/base/v3/REST/contact/user%2Btag%40example.com?");
        assertThat(url).contains("Limit=25", "CountOnly=false");
        assertThat(contactRequest.queryString()).contains("Limit=25", "CountOnly=false");
        assertThat(contactRequest.getApiVersion()).isEqualTo(ApiVersion.V3);
        assertThat(contactRequest.getAuthenticationType()).isEqualTo(ApiAuthenticationType.Basic);
        assertThat(contactRequest.getResource()).isEqualTo("contact");

        JSONObject body = contactRequest.getBodyJSON();
        assertThat(body.getString(Contact.EMAIL)).isEqualTo("user+tag@example.com");
        assertThat(body.getJSONArray("Tags").toList()).containsExactly("native", "mailjet");
        assertThat(new JSONObject(contactRequest.getBody()).getJSONArray("Tags").length()).isEqualTo(2);

        HashMap<String, Object> mapBody = new HashMap<>();
        mapBody.put("Name", "Map Body");
        assertThat(new MailjetRequest(Contact.resource).setBody(mapBody).getBodyJSON().getString("Name"))
                .isEqualTo("Map Body");
    }

    @Test
    void clientSendsAuthenticatedGetAndParsesMailjetResponse() throws Exception {
        try (TestServer server = TestServer.respondingWith(200, """
                {
                  "Count": 1,
                  "Total": 1,
                  "Greeting": "hello",
                  "Data": [ { "Email": "reader@example.com", "ID": 42 } ]
                }
                """);
                TestHttpClient httpClient = TestHttpClient.create()) {
            MailjetClient client = new MailjetClient(ClientOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey("key")
                    .apiSecretKey("secret")
                    .okHttpClient(httpClient.client())
                    .build());
            MailjetRequest request = new MailjetRequest(Contact.resource)
                    .filter(Contact.LIMIT, 1)
                    .filter(Contact.OFFSET, 2);

            MailjetResponse response = client.get(request);

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.method()).isEqualTo("GET");
            assertThat(recorded.path()).isEqualTo("/v3/REST/contact");
            assertThat(recorded.query()).contains("Limit=1", "Offset=2");
            assertThat(recorded.headers().getFirst("Accept")).isEqualTo("application/json");
            assertThat(recorded.headers().getFirst("User-Agent")).startsWith("mailjet-apiv3-java/");
            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("key:secret".getBytes(StandardCharsets.UTF_8));
            assertThat(recorded.headers().getFirst("Authorization")).isEqualTo(expectedAuthorization);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getCount()).isEqualTo(1);
            assertThat(response.getTotal()).isEqualTo(1);
            assertThat(response.getString("Greeting")).isEqualTo("hello");
            assertThat(response.getData().getJSONObject(0).getString("Email")).isEqualTo("reader@example.com");
        }
    }

    @Test
    void transactionalEmailRequestPostsSerializedMessageAndConvertsResponse() throws Exception {
        try (TestServer server = TestServer.respondingWith(201, """
                {
                  "Messages": [ {
                    "Status": "success",
                    "CustomID": "custom-123",
                    "To": [ {
                      "Email": "recipient@example.com",
                      "MessageUUID": "uuid-1",
                      "MessageID": 12345,
                      "MessageHref": "https://api.mailjet.test/message/12345"
                    } ]
                  } ]
                }
                """);
                TestHttpClient httpClient = TestHttpClient.create()) {
            MailjetClient client = new MailjetClient(ClientOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey("mailjet-key")
                    .apiSecretKey("mailjet-secret")
                    .okHttpClient(httpClient.client())
                    .build());
            Attachment attachment = Attachment.fromInputStream(
                    new ByteArrayInputStream("report".getBytes(StandardCharsets.UTF_8)),
                    "report.txt",
                    "text/plain");
            TransactionalEmail email = TransactionalEmail.builder()
                    .from(new SendContact("sender@example.com", "Sender"))
                    .to(new SendContact("recipient@example.com", "Recipient"))
                    .cc(new SendContact("copy@example.com"))
                    .subject("Integration test")
                    .textPart("Plain body")
                    .htmlPart("<strong>HTML body</strong>")
                    .attachment(attachment)
                    .trackOpens(TrackOpens.ENABLED)
                    .trackClicks(TrackClicks.DISABLED)
                    .customID("custom-123")
                    .variable("invoice", 7)
                    .header("X-Test", "mailjet")
                    .build();
            SendEmailsRequest request = SendEmailsRequest.builder()
                    .message(email)
                    .sandboxMode(true)
                    .advanceErrorHandling(true)
                    .build();

            SendEmailsResponse response = request.sendWith(client);

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.method()).isEqualTo("POST");
            assertThat(recorded.path()).isEqualTo("/v3.1/send");
            JSONObject posted = new JSONObject(recorded.body());
            assertThat(posted.getBoolean("SandboxMode")).isTrue();
            assertThat(posted.getBoolean("AdvanceErrorHandling")).isTrue();
            JSONArray messages = posted.getJSONArray("Messages");
            JSONObject message = messages.getJSONObject(0);
            assertThat(message.getString("Subject")).isEqualTo("Integration test");
            assertThat(message.getString("TrackOpens")).isEqualTo("enabled");
            assertThat(message.getString("TrackClicks")).isEqualTo("disabled");
            assertThat(message.getJSONObject("From").getString("Email")).isEqualTo("sender@example.com");
            assertThat(message.getJSONArray("To").getJSONObject(0).getString("Name")).isEqualTo("Recipient");
            assertThat(message.getJSONArray("Attachments").getJSONObject(0).getString("Base64Content"))
                    .isEqualTo(Base64.getEncoder().encodeToString("report".getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getJSONObject("Variables").getInt("invoice")).isEqualTo(7);
            assertThat(message.getJSONObject("Headers").getString("X-Test")).isEqualTo("mailjet");
            assertThat(response.getMessages()).hasSize(1);
            assertThat(response.getMessages()[0].getStatus()).isEqualTo(SentMessageStatus.SUCCESS);
            assertThat(response.getMessages()[0].getCustomID()).isEqualTo("custom-123");
            assertThat(response.getMessages()[0].getTo().get(0).getMessageID()).isEqualTo(12345);
        }
    }

    @Test
    void asyncPutSendsJsonBodyAndCompletesWithParsedResponse() throws Exception {
        try (TestServer server = TestServer.respondingWith(200, """
                {
                  "Count": 1,
                  "Total": 1,
                  "Data": [ { "ID": 123, "Name": "Updated list" } ]
                }
                """);
                TestHttpClient httpClient = TestHttpClient.create()) {
            MailjetClient client = new MailjetClient(ClientOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey("key")
                    .apiSecretKey("secret")
                    .okHttpClient(httpClient.client())
                    .build());
            MailjetRequest request = new MailjetRequest(Contactslist.resource, 123L)
                    .property(Contactslist.NAME, "Updated list");

            CompletableFuture<MailjetResponse> future = client.putAsync(request);
            MailjetResponse response = future.get(10, TimeUnit.SECONDS);

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.method()).isEqualTo("PUT");
            assertThat(recorded.path()).isEqualTo("/v3/REST/contactslist/123");
            assertThat(recorded.headers().getFirst("Content-Type")).startsWith("application/json");
            assertThat(new JSONObject(recorded.body()).getString(Contactslist.NAME)).isEqualTo("Updated list");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getData().getJSONObject(0).getString(Contactslist.NAME)).isEqualTo("Updated list");
        }
    }

    @Test
    void csvDataPostReadsFileDataAndSendsEncodedPlainTextBody() throws Exception {
        Path csvFile = Files.createTempFile("mailjet-contacts", ".csv");
        try {
            String csvData = "email,name\nreader@example.com,Reader\n";
            Files.writeString(csvFile, csvData, StandardCharsets.UTF_8);
            Resource csvDataResource = new Resource(
                    "csvimport",
                    "csvdata",
                    ApiVersion.V3,
                    ApiAuthenticationType.Basic);
            try (TestServer server = TestServer.respondingWith(201, """
                    {
                      "Count": 1,
                      "Total": 1,
                      "Data": [ { "ID": 77, "Status": "Upload" } ]
                    }
                    """);
                    TestHttpClient httpClient = TestHttpClient.create()) {
                MailjetClient client = new MailjetClient(ClientOptions.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("key")
                        .apiSecretKey("secret")
                        .okHttpClient(httpClient.client())
                        .build());
                MailjetRequest request = new MailjetRequest(csvDataResource, 77L)
                        .filter(Csvimport.ERRTRESHOLD, 5)
                        .setData(csvFile.toString());

                MailjetResponse response = client.post(request);

                RecordedRequest recorded = server.takeRequest();
                assertThat(recorded.method()).isEqualTo("POST");
                assertThat(recorded.path()).isEqualTo("/v3/DATA/csvimport/77/csvdata/text:plain");
                assertThat(recorded.query()).contains("ErrTreshold=5");
                assertThat(recorded.headers().getFirst("Content-Type")).startsWith("text/plain");
                assertThat(recorded.body())
                        .isEqualTo(Base64.getEncoder().encodeToString(csvData.getBytes(StandardCharsets.UTF_8)));
                assertThat(request.getContentType()).isEqualTo("text/plain");
                assertThat(response.getStatus()).isEqualTo(201);
                assertThat(response.getData().getJSONObject(0).getString(Csvimport.STATUS)).isEqualTo("Upload");
            }
        } finally {
            Files.deleteIfExists(csvFile);
        }
    }

    @Test
    void publicResponseAndAttachmentModelsExposeBeanState() {
        Attachment attachment = Attachment.builder()
                .filename("inline.txt")
                .contentType("text/plain")
                .base64Content("aW5saW5l")
                .contentID("cid-1")
                .build();
        attachment.setFilename("renamed.txt");
        attachment.setContentType("text/custom");
        attachment.setBase64Content("cmVuYW1lZA==");
        attachment.setContentID("cid-2");

        EmailResult emailResult = new EmailResult();
        emailResult.setEmail("delivered@example.com");
        emailResult.setMessageUUID("uuid-2");
        emailResult.setMessageID(6789L);
        emailResult.setMessageHref("https://api.mailjet.test/message/6789");
        SendEmailError error = new SendEmailError();
        error.setErrorIdentifier("id-1");
        error.setErrorCode("mj-001");
        error.setStatusCode(400);
        error.setErrorMessage("Invalid recipient");
        error.setErrorRelatedTo(new String[] {"To[0].Email"});
        MessageResult messageResult = new MessageResult();
        messageResult.setStatus(SentMessageStatus.ERROR);
        messageResult.setCustomID("custom-error");
        messageResult.setTo(List.of(emailResult));
        messageResult.setErrors(new SendEmailError[] {error});
        SendEmailsResponse response = new SendEmailsResponse();
        response.setMessages(new MessageResult[] {messageResult});

        assertThat(attachment.getFilename()).isEqualTo("renamed.txt");
        assertThat(attachment.getContentType()).isEqualTo("text/custom");
        assertThat(attachment.getBase64Content()).isEqualTo("cmVuYW1lZA==");
        assertThat(attachment.getContentID()).isEqualTo("cid-2");
        assertThat(response.getMessages()[0].getStatus()).isEqualTo(SentMessageStatus.ERROR);
        assertThat(response.getMessages()[0].getTo()).containsExactly(emailResult);
        assertThat(response.getMessages()[0].getErrors()[0].getErrorRelatedTo()).containsExactly("To[0].Email");
        assertThat(error.getStatusCode()).isEqualTo(400);
        assertThat(emailResult.getMessageUUID()).isEqualTo("uuid-2");
    }

    @Test
    void customBearerResourceCanBeDeletedWithoutNamespace() throws Exception {
        Resource bearerResource = new Resource(
                "sms-send",
                "",
                ApiVersion.V4,
                ApiAuthenticationType.Bearer,
                true);
        try (TestServer server = TestServer.respondingWith(204, "");
                TestHttpClient httpClient = TestHttpClient.create()) {
            MailjetClient client = new MailjetClient(ClientOptions.builder()
                    .baseUrl(server.baseUrl())
                    .bearerAccessToken("token-123")
                    .okHttpClient(httpClient.client())
                    .build());

            MailjetResponse response = client.delete(new MailjetRequest(bearerResource, 99L));

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.method()).isEqualTo("DELETE");
            assertThat(recorded.path()).isEqualTo("/v4/sms-send/99");
            assertThat(recorded.headers().getFirst("Authorization")).isEqualTo("Bearer token-123");
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getInt("status")).isEqualTo(204);
        }
    }

    private static final class TestHttpClient implements AutoCloseable {
        private final OkHttpClient client;

        private TestHttpClient(OkHttpClient client) {
            this.client = client;
        }

        static TestHttpClient create() {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .build();
            return new TestHttpClient(client);
        }

        OkHttpClient client() {
            return client;
        }

        @Override
        public void close() {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }

    private record RecordedRequest(String method, String path, String query, Headers headers, String body) {
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final LinkedBlockingQueue<RecordedRequest> requests;

        private TestServer(HttpServer server, ExecutorService executor, LinkedBlockingQueue<RecordedRequest> requests) {
            this.server = server;
            this.executor = executor;
            this.requests = requests;
        }

        static TestServer respondingWith(int status, String body) throws IOException {
            LinkedBlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> handle(exchange, status, body, requests));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.start();
            return new TestServer(server, executor, requests);
        }

        String baseUrl() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        RecordedRequest takeRequest() throws InterruptedException {
            RecordedRequest request = requests.poll(10, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void handle(
                HttpExchange exchange,
                int status,
                String body,
                LinkedBlockingQueue<RecordedRequest> requests) throws IOException {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders(),
                    requestBody));
            byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBody.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(responseBody);
            }
        }
    }
}
