/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * The `Call` lifecycle: inspecting the request a call will send before it runs, single-use
 * enforcement of executed calls, cancellation of pending and in-flight synchronous and enqueued
 * calls, and request-creation failures that a call caches and reports on every attempt.
 *
 * A reporting dashboard schedules long-running report exports and downloads their results. It
 * previews an export request before submitting it, lets the user abort a download that hangs
 * either on a worker thread or in the background, and reports a missing report id once, without
 * a request ever reaching the backend. The backend is a `MockWebServer` whose request count
 * shows which calls really went out.
 */
public class ReportExportCallLifecycleTest {

    private MockWebServer server;
    private ReportApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/reports/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(ReportApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void previewsTheExportRequestBeforeSubmittingIt() throws Exception {
        server.enqueue(json(202, """
                {"id": "exp-41", "status": "QUEUED"}
                """));

        Call<ExportJob> call = api.scheduleExport("Bearer analyst", new ExportSpec("revenue", "csv"));
        Request preview = call.request();

        assertEquals("POST", preview.method());
        assertEquals(server.url("/reports/exports"), preview.url());
        assertEquals("Bearer analyst", preview.header("Authorization"));
        assertEquals("application/json; charset=UTF-8", preview.body().contentType().toString());
        Buffer previewBody = new Buffer();
        preview.body().writeTo(previewBody);
        assertEquals("{\"report\":\"revenue\",\"format\":\"csv\"}", previewBody.readUtf8());
        assertSame(preview, call.request());
        assertFalse(call.isExecuted());
        assertEquals(0, server.getRequestCount());

        Response<ExportJob> response = call.execute();

        assertEquals(202, response.code());
        assertEquals("exp-41", response.body().id);
        assertEquals("QUEUED", response.body().status);
        RecordedRequest sent = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("/reports/exports", sent.getPath());
        assertEquals("{\"report\":\"revenue\",\"format\":\"csv\"}", sent.getBody().readUtf8());
    }

    @Test
    void anExecutedCallCannotRunAgainButItsCloneCan() throws Exception {
        server.enqueue(json(200, """
                {"id": "exp-41", "status": "RUNNING"}
                """));
        server.enqueue(json(200, """
                {"id": "exp-41", "status": "DONE"}
                """));

        Call<ExportJob> poll = api.exportStatus("exp-41");
        assertEquals("RUNNING", poll.execute().body().status);

        IllegalStateException again = assertThrows(IllegalStateException.class, poll::execute);
        assertEquals("Already executed.", again.getMessage());
        IllegalStateException enqueued = assertThrows(IllegalStateException.class,
                () -> poll.enqueue(new IgnoringCallback<>()));
        assertEquals("Already executed.", enqueued.getMessage());

        Call<ExportJob> nextPoll = poll.clone();
        assertFalse(nextPoll.isExecuted());
        assertEquals("DONE", nextPoll.execute().body().status);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void aCallCancelledBeforeItRunsNeverReachesTheBackend() {
        Call<ExportJob> poll = api.exportStatus("exp-41");
        poll.cancel();

        assertTrue(poll.isCanceled());
        IOException canceled = assertThrows(IOException.class, poll::execute);
        assertEquals("Canceled", canceled.getMessage());
        assertTrue(poll.isExecuted());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void cancellingAHangingSynchronousDownloadUnblocksTheWorkerThread() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Call<ExportJob> download = api.exportStatus("exp-77");

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Response<ExportJob>> pending = worker.submit(download::execute);
            RecordedRequest arrived = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(arrived);
            assertEquals("/reports/exports/exp-77", arrived.getPath());

            download.cancel();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> pending.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(download.isCanceled());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void cancellingAHangingBackgroundDownloadReportsAFailureToTheCallback() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Call<ExportJob> download = api.exportStatus("exp-78");
        RecordingCallback<ExportJob> callback = new RecordingCallback<>();

        download.enqueue(callback);
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        download.cancel();

        assertTrue(callback.done.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, callback.failure.get());
        assertNull(callback.response.get());
        assertTrue(download.isCanceled());
    }

    @Test
    void aMissingReportIdFailsTheCallOnEveryAttemptWithoutSendingIt() throws Exception {
        Call<ExportJob> broken = api.exportStatus(null);

        IllegalArgumentException fromPreview = assertThrows(IllegalArgumentException.class,
                broken::request);
        assertEquals("Path parameter \"id\" value must not be null.", fromPreview.getMessage());
        IllegalArgumentException fromExecute = assertThrows(IllegalArgumentException.class,
                broken::execute);
        assertSame(fromPreview, fromExecute);

        Call<ExportJob> brokenInBackground = api.exportStatus(null);
        RecordingCallback<ExportJob> callback = new RecordingCallback<>();
        brokenInBackground.enqueue(callback);

        assertTrue(callback.done.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, callback.failure.get());
        assertEquals("Path parameter \"id\" value must not be null.",
                callback.failure.get().getMessage());
        assertEquals(0, server.getRequestCount());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    interface ReportApi {
        @POST("exports")
        Call<ExportJob> scheduleExport(@Header("Authorization") String token, @Body ExportSpec spec);

        @GET("exports/{id}")
        Call<ExportJob> exportStatus(@Path("id") String id);
    }

    static final class ExportSpec {
        final String report;
        final String format;

        ExportSpec(String report, String format) {
            this.report = report;
            this.format = format;
        }
    }

    static final class ExportJob {
        String id;
        String status;
    }

    /// Records the single outcome of an enqueued call and releases a latch when it arrives.
    static final class RecordingCallback<T> implements Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Response<T>> response = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onResponse(Call<T> call, Response<T> received) {
            response.set(received);
            done.countDown();
        }

        @Override
        public void onFailure(Call<T> call, Throwable error) {
            failure.set(error);
            done.countDown();
        }
    }

    /// A callback for calls that are expected to be rejected before they are dispatched.
    static final class IgnoringCallback<T> implements Callback<T> {
        @Override
        public void onResponse(Call<T> call, Response<T> received) {
        }

        @Override
        public void onFailure(Call<T> call, Throwable error) {
        }
    }
}
