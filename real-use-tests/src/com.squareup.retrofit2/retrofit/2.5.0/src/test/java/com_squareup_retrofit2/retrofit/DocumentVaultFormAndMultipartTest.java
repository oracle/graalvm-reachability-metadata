/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

/**
 * Form-encoded and multipart request bodies that Retrofit assembles from `@Field` and `@Part`
 * parameters, together with streamed raw `ResponseBody` downloads from a dynamic `@Url`.
 *
 * A document-vault client signs in with an OAuth password-grant form, uploads a file with its
 * metadata as a multipart request, attaches several files to an existing document, and streams
 * the stored file back from the download link the vault hands out. The vault is a
 * `MockWebServer`; every body Retrofit writes is read back off the wire and checked.
 */
public class DocumentVaultFormAndMultipartTest {

    private static final MediaType PDF = MediaType.parse("application/pdf");
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

    private MockWebServer server;
    private DocumentVaultApi api;

    @BeforeEach
    void startVault() throws IOException {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/vault/"))
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        api = retrofit.create(DocumentVaultApi.class);
    }

    @AfterEach
    void stopVault() throws IOException {
        server.shutdown();
    }

    @Test
    void signsInWithAUrlEncodedPasswordGrantForm() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/plain")
                .setBody("token-7f3a"));
        Map<String, String> client = new LinkedHashMap<>();
        client.put("client_id", "vault-desktop");
        client.put("redirect_uri", "app://callback?x=1");

        Response<String> response = api.requestToken(
                "password", "ana@example.com", "s3cret & safe", List.of("read", "write"), client)
                .execute();

        assertTrue(response.isSuccessful());
        assertEquals("token-7f3a", response.body());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/vault/oauth/token", request.getPath());
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"));
        assertEquals(
                "grant_type=password&username=ana%40example.com&password=s3cret%20%26%20safe"
                        + "&scope=read&scope=write&client_id=vault-desktop"
                        + "&redirect_uri=app%3A%2F%2Fcallback%3Fx%3D1",
                request.getBody().readUtf8());
    }

    @Test
    void uploadsADocumentWithItsMetadataAsAMultipartRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("doc-501"));
        byte[] pdf = "%PDF-1.4 quarterly report".getBytes(StandardCharsets.US_ASCII);
        MultipartBody.Part file = MultipartBody.Part.createFormData(
                "file", "q3-report.pdf", RequestBody.create(PDF, pdf));
        Map<String, RequestBody> tags = new LinkedHashMap<>();
        tags.put("tag[finance]", RequestBody.create(TEXT, "yes"));
        tags.put("tag[archive]", RequestBody.create(TEXT, "2026"));

        Response<String> response = api.upload("Q3 report", file, tags).execute();

        assertEquals(201, response.code());
        assertEquals("doc-501", response.body());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/vault/documents", request.getPath());
        String contentType = request.getHeader("Content-Type");
        assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
        String boundary = contentType.substring(contentType.indexOf('=') + 1);
        String body = request.getBody().readUtf8();
        assertEquals(6, body.split("--" + boundary, -1).length, body);
        assertTrue(body.contains("""
                Content-Disposition: form-data; name="title"\r
                Content-Transfer-Encoding: binary\r
                Content-Type: text/plain; charset=UTF-8\r
                Content-Length: 9\r
                \r
                Q3 report\r
                """), body);
        assertTrue(body.contains("""
                Content-Disposition: form-data; name="file"; filename="q3-report.pdf"\r
                Content-Type: application/pdf\r
                Content-Length: 25\r
                \r
                %PDF-1.4 quarterly report\r
                """), body);
        assertTrue(body.contains("""
                Content-Disposition: form-data; name="tag[finance]"\r
                Content-Transfer-Encoding: binary\r
                Content-Type: text/plain; charset=utf-8\r
                Content-Length: 3\r
                \r
                yes\r
                """), body);
        assertTrue(body.contains("name=\"tag[archive]\""), body);
        assertTrue(body.endsWith("--" + boundary + "--\r\n"), body);
    }

    @Test
    void attachesSeveralFilesToAnExistingDocument() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        List<MultipartBody.Part> attachments = List.of(
                MultipartBody.Part.createFormData(
                        "attachment", "summary.txt", RequestBody.create(TEXT, "Revenue up 4%")),
                MultipartBody.Part.createFormData(
                        "attachment", "chart.pdf", RequestBody.create(PDF, "%PDF-1.4 chart")));

        Response<Void> response = api.attach(501L, "Reviewed by finance", attachments).execute();

        assertEquals(204, response.code());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PUT", request.getMethod());
        assertEquals("/vault/documents/501/attachments", request.getPath());
        String body = request.getBody().readUtf8();
        int note = body.indexOf("name=\"note\"");
        int summary = body.indexOf("filename=\"summary.txt\"");
        int chart = body.indexOf("filename=\"chart.pdf\"");
        assertTrue(note >= 0 && note < summary && summary < chart, body);
        assertTrue(body.contains("\r\n\r\nReviewed by finance\r\n"), body);
        assertTrue(body.contains("\r\n\r\nRevenue up 4%\r\n"), body);
        assertTrue(body.contains("\r\n\r\n%PDF-1.4 chart\r\n"), body);
    }

    @Test
    void streamsAStoredFileFromTheDownloadLinkTheVaultHandsOut() throws Exception {
        byte[] stored = new byte[64 * 1024];
        for (int i = 0; i < stored.length; i++) {
            stored[i] = (byte) (i * 31);
        }
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/pdf")
                .setBody(new Buffer().write(stored)));
        String downloadLink = server.url("/blobs/9c1e/q3-report.pdf?sig=abc").toString();

        Response<ResponseBody> response = api.download(downloadLink).execute();

        assertTrue(response.isSuccessful());
        byte[] received;
        try (ResponseBody body = response.body(); InputStream in = body.byteStream()) {
            assertEquals(PDF, body.contentType());
            assertEquals(stored.length, body.contentLength());
            received = in.readAllBytes();
        }
        assertArrayEquals(stored, received);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/blobs/9c1e/q3-report.pdf?sig=abc", request.getPath());
    }

    /** The document-vault API as the application declares it. */
    interface DocumentVaultApi {

        @FormUrlEncoded
        @POST("oauth/token")
        Call<String> requestToken(
                @Field("grant_type") String grantType,
                @Field("username") String username,
                @Field("password") String password,
                @Field("scope") List<String> scopes,
                @FieldMap Map<String, String> client);

        @Multipart
        @POST("documents")
        Call<String> upload(
                @Part("title") String title,
                @Part MultipartBody.Part file,
                @PartMap Map<String, RequestBody> tags);

        @Multipart
        @PUT("documents/{id}/attachments")
        Call<Void> attach(
                @Path("id") long id,
                @Part("note") String note,
                @Part List<MultipartBody.Part> attachments);

        @Streaming
        @GET
        Call<ResponseBody> download(@Url String url);
    }
}
