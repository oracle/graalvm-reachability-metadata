/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Gson as the JSON conversion layer of an HTTP API client, configured with a field naming policy
 * and a custom type adapter.
 *
 * An issue tracker client talks to a REST service through Retrofit's Gson converter: the
 * application-built `Gson` maps snake_case wire names onto camelCase fields, writes and reads
 * `Instant` timestamps through a registered `TypeAdapter`, and turns error bodies into a typed
 * error. A local `MockWebServer` plays the service, so each test asserts both on the JSON the
 * client sent and on the objects it decoded.
 */
public class IssueTrackerHttpClientConversionTest {

    private MockWebServer server;

    private IssueTrackerApi api;

    private Retrofit retrofit;

    @BeforeEach
    void startService() throws IOException {
        server = new MockWebServer();
        server.start();
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(Instant.class, new IsoInstantAdapter().nullSafe())
                .create();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/api/"))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        api = retrofit.create(IssueTrackerApi.class);
    }

    @AfterEach
    void stopService() throws IOException {
        server.shutdown();
    }

    @Test
    void decodesAListOfIssuesFromSnakeCaseJson() throws Exception {
        server.enqueue(json(200, """
                [
                  {"issue_id": 101, "title": "Login fails on Safari", "status": "open",
                   "assignee_login": "dana", "labels": ["bug", "auth"],
                   "opened_at": "2026-03-14T09:30:00Z"},
                  {"issue_id": 102, "title": "Dark mode", "status": "in_progress",
                   "labels": [], "opened_at": "2026-03-15T17:05:12Z"}
                ]
                """));

        Response<List<Issue>> response = api.listIssues("web", Status.OPEN).execute();

        assertTrue(response.isSuccessful());
        List<Issue> issues = response.body();
        assertNotNull(issues);
        assertEquals(2, issues.size());
        Issue login = issues.get(0);
        assertEquals(101, login.issueId);
        assertEquals("Login fails on Safari", login.title);
        assertEquals(Status.OPEN, login.status);
        assertEquals("dana", login.assigneeLogin);
        assertEquals(List.of("bug", "auth"), login.labels);
        assertEquals(Instant.parse("2026-03-14T09:30:00Z"), login.openedAt);
        Issue darkMode = issues.get(1);
        assertEquals(Status.IN_PROGRESS, darkMode.status);
        assertNull(darkMode.assigneeLogin);
        assertTrue(darkMode.labels.isEmpty());

        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/projects/web/issues?status=OPEN", request.getPath());
    }

    @Test
    void encodesANewIssueAndDecodesTheCreatedOne() throws Exception {
        server.enqueue(json(201, """
                {"issue_id": 230, "title": "Export to CSV", "status": "open",
                 "assignee_login": "lee", "labels": ["feature"],
                 "opened_at": "2026-04-02T12:00:00Z"}
                """));

        NewIssue draft = new NewIssue();
        draft.title = "Export to CSV";
        draft.assigneeLogin = "lee";
        draft.labels = List.of("feature");
        draft.dueAt = Instant.parse("2026-05-01T00:00:00Z");
        Response<Issue> response = api.createIssue("web", draft).execute();

        assertEquals(201, response.code());
        Issue created = response.body();
        assertNotNull(created);
        assertEquals(230, created.issueId);
        assertEquals("lee", created.assigneeLogin);
        assertEquals(Instant.parse("2026-04-02T12:00:00Z"), created.openedAt);

        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/projects/web/issues", request.getPath());
        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("application/json"), contentType);
        JsonObject sent = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("Export to CSV", sent.get("title").getAsString());
        assertEquals("lee", sent.get("assignee_login").getAsString());
        assertEquals("feature", sent.getAsJsonArray("labels").get(0).getAsString());
        assertEquals("2026-05-01T00:00:00Z", sent.get("due_at").getAsString());
        assertFalse(sent.has("assigneeLogin"), "the naming policy must rename every field");
        assertFalse(sent.has("milestone"), "null fields are omitted by default");
    }

    @Test
    void decodesTheErrorBodyAndRejectsAMalformedIssue() throws Exception {
        server.enqueue(json(404, """
                {"error_code": "ISSUE_NOT_FOUND", "message": "No issue 999 in project web"}
                """));

        Response<Issue> missing = api.getIssue("web", 999).execute();

        assertFalse(missing.isSuccessful());
        assertEquals(404, missing.code());
        assertNull(missing.body());
        Converter<ResponseBody, ApiError> errorConverter =
                retrofit.responseBodyConverter(ApiError.class, new Annotation[0]);
        ApiError error;
        try (ResponseBody errorBody = missing.errorBody()) {
            assertNotNull(errorBody);
            error = errorConverter.convert(errorBody);
        }
        assertNotNull(error);
        assertEquals("ISSUE_NOT_FOUND", error.errorCode());
        assertEquals("No issue 999 in project web", error.message());
        assertEquals("/api/projects/web/issues/999", takeRequest().getPath());

        server.enqueue(json(200, """
                {"issue_id": "one-hundred", "title": "Broken payload", "status": "open"}
                """));
        Call<Issue> malformed = api.getIssue("web", 100);

        JsonSyntaxException rejected = assertThrows(JsonSyntaxException.class, malformed::execute);
        assertTrue(rejected.getCause() instanceof NumberFormatException, String.valueOf(rejected.getCause()));
        assertEquals("/api/projects/web/issues/100", takeRequest().getPath());
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(request, "the client must have sent a request");
        return request;
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    /// The REST endpoints the client calls.
    interface IssueTrackerApi {

        @GET("projects/{project}/issues")
        Call<List<Issue>> listIssues(@Path("project") String project, @Query("status") Status status);

        @GET("projects/{project}/issues/{id}")
        Call<Issue> getIssue(@Path("project") String project, @Path("id") int id);

        @POST("projects/{project}/issues")
        Call<Issue> createIssue(@Path("project") String project, @Body NewIssue issue);
    }

    /// Writes and reads timestamps as ISO-8601 strings.
    static final class IsoInstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.parse(in.nextString());
        }
    }

    static final class Issue {
        int issueId;
        String title;
        Status status;
        String assigneeLogin;
        List<String> labels;
        Instant openedAt;
    }

    static final class NewIssue {
        String title;
        String assigneeLogin;
        String milestone;
        List<String> labels;
        Instant dueAt;
    }

    record ApiError(String errorCode, String message) {
    }

    enum Status {
        @SerializedName("open") OPEN,
        @SerializedName("in_progress") IN_PROGRESS,
        @SerializedName("closed") CLOSED
    }
}
