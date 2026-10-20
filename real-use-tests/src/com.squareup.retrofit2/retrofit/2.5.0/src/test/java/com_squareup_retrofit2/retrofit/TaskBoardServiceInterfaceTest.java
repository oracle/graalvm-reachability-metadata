/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * Service interface proxies built by `Retrofit.create`, whose annotated methods become HTTP
 * requests with Gson-converted JSON bodies and are run as synchronous or enqueued `Call`s.
 *
 * A task-board client lists, creates, updates, and deletes tasks against a REST backend. The
 * backend is a `MockWebServer`; every request Retrofit builds from the interface annotations is
 * read back off the wire and checked, as are the typed results and the error responses the
 * client turns into user-facing messages.
 */
public class TaskBoardServiceInterfaceTest {

    private MockWebServer server;
    private TaskBoardApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(TaskBoardApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void listsTasksOfABoardFilteredByQueryParameters() throws Exception {
        server.enqueue(json(200, """
                [
                  {"id": 7, "title": "Write release notes", "done": false, "labels": ["docs"]},
                  {"id": 9, "title": "Tag 2.5.0", "done": false, "labels": ["release", "ops"]}
                ]
                """));
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("sort", "title");
        filters.put("label", "release notes");

        Response<List<Task>> response = api.listTasks("platform", false, filters).execute();

        assertTrue(response.isSuccessful());
        List<Task> tasks = response.body();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        assertEquals("Write release notes", tasks.get(0).title);
        assertEquals(List.of("release", "ops"), tasks.get(1).labels);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals(
                "/api/v1/boards/platform/tasks?done=false&sort=title&label=release%20notes",
                request.getPath());
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("task-board/1.0", request.getHeader("User-Agent"));
    }

    @Test
    void createsATaskFromAJsonBodyAndReadsTheServerAssignedId() throws Exception {
        server.enqueue(json(201, """
                {"id": 42, "title": "Rotate signing keys", "done": false, "labels": ["security"]}
                """).addHeader("Location", "/api/v1/boards/platform/tasks/42"));
        Task draft = new Task();
        draft.title = "Rotate signing keys";
        draft.labels = List.of("security");

        Response<Task> response = api.createTask("platform", "req-1138", draft).execute();

        assertEquals(201, response.code());
        assertEquals("/api/v1/boards/platform/tasks/42", response.headers().get("Location"));
        Task created = response.body();
        assertNotNull(created);
        assertEquals(42L, created.id);
        assertFalse(created.done);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/boards/platform/tasks", request.getPath());
        assertEquals("req-1138", request.getHeader("X-Request-Id"));
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"));
        assertEquals(
                "{\"id\":0,\"title\":\"Rotate signing keys\",\"done\":false,\"labels\":[\"security\"]}",
                request.getBody().readUtf8());
    }

    @Test
    void completesATaskAsynchronouslyThroughAnEnqueuedCall() throws Exception {
        server.enqueue(json(200, """
                {"id": 9, "title": "Tag 2.5.0", "done": true, "labels": ["release"]}
                """));
        TaskPatch patch = new TaskPatch();
        patch.done = true;
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Response<Task>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        api.updateTask("platform", 9L, patch).enqueue(new Callback<Task>() {
            @Override
            public void onResponse(Call<Task> call, Response<Task> response) {
                result.set(response);
                finished.countDown();
            }

            @Override
            public void onFailure(Call<Task> call, Throwable t) {
                failure.set(t);
                finished.countDown();
            }
        });

        assertTrue(finished.await(10, TimeUnit.SECONDS));
        assertNull(failure.get());
        Task updated = result.get().body();
        assertNotNull(updated);
        assertTrue(updated.done);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PATCH", request.getMethod());
        assertEquals("/api/v1/boards/platform/tasks/9", request.getPath());
        assertEquals("{\"done\":true}", request.getBody().readUtf8());
    }

    @Test
    void turnsAnErrorResponseIntoTheBackendProblemMessage() throws Exception {
        server.enqueue(json(404, """
                {"code": "task_not_found", "message": "No task 404 on board platform"}
                """));
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        TaskBoardApi client = retrofit.create(TaskBoardApi.class);

        Response<Void> response = client.deleteTask("platform", 404L).execute();

        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
        assertNull(response.body());
        Problem problem = retrofit.<Problem>responseBodyConverter(Problem.class, new Annotation[0])
                .convert(response.errorBody());
        assertNotNull(problem);
        assertEquals("task_not_found", problem.code);
        assertEquals("No task 404 on board platform", problem.message);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("DELETE", request.getMethod());
        assertEquals("/api/v1/boards/platform/tasks/404", request.getPath());
    }

    @Test
    void clonesACallToRetryItAfterTheFirstAttemptFailed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("busy"));
        server.enqueue(json(200, """
                {"id": 7, "title": "Write release notes", "done": false, "labels": []}
                """));

        Call<Task> first = api.getTask("platform", 7L);
        Response<Task> unavailable = first.execute();
        assertEquals(503, unavailable.code());
        assertEquals("busy", unavailable.errorBody().string());
        assertTrue(first.isExecuted());

        Call<Task> retry = first.clone();
        assertFalse(retry.isExecuted());
        assertEquals(first.request().url(), retry.request().url());
        Response<Task> recovered = retry.execute();

        assertTrue(recovered.isSuccessful());
        assertEquals("Write release notes", recovered.body().title);
        assertEquals(2, server.getRequestCount());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** The task-board REST API as the application declares it. */
    interface TaskBoardApi {

        @Headers({"Accept: application/json", "User-Agent: task-board/1.0"})
        @GET("boards/{board}/tasks")
        Call<List<Task>> listTasks(
                @Path("board") String board,
                @Query("done") boolean done,
                @QueryMap Map<String, String> filters);

        @GET("boards/{board}/tasks/{id}")
        Call<Task> getTask(@Path("board") String board, @Path("id") long id);

        @POST("boards/{board}/tasks")
        Call<Task> createTask(
                @Path("board") String board,
                @Header("X-Request-Id") String requestId,
                @Body Task task);

        @PATCH("boards/{board}/tasks/{id}")
        Call<Task> updateTask(@Path("board") String board, @Path("id") long id, @Body TaskPatch patch);

        @DELETE("boards/{board}/tasks/{id}")
        Call<Void> deleteTask(@Path("board") String board, @Path("id") long id);
    }

    /** A task as the backend stores it. */
    static final class Task {
        long id;
        String title;
        boolean done;
        List<String> labels;
    }

    /** A partial update; Gson leaves out the fields left null. */
    static final class TaskPatch {
        String title;
        Boolean done;
    }

    /** The problem document the backend returns with an error status. */
    static final class Problem {
        String code;
        String message;
    }
}
