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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.QoSHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jetty's quality-of-service admission subsystem to bound concurrent report exports and reject overload.
public class ReportExportAdmissionTest {

    @Test
    @Timeout(60)
    public void queuesOneExportAndRejectsExcessWorkUntilCapacityIsAvailable() throws Exception {
        BlockingQueue<ExportJob> jobs = new LinkedBlockingQueue<>();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        QoSHandler admission = new QoSHandler(new ReportHandler(jobs));
        admission.setMaxRequestCount(1);
        admission.setMaxSuspendedRequestCount(1);
        admission.setMaxSuspend(Duration.ofSeconds(30));
        server.setHandler(admission);

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                try {
                    CompletableFuture<HttpResponse<String>> daily = export(client, base, "daily");
                    ExportJob dailyJob = nextJob(jobs, "daily");

                    // The asynchronous export holds its admission slot until its response completes.
                    CompletableFuture<HttpResponse<String>> weekly = export(client, base, "weekly");
                    awaitQueuedExport(admission);
                    assertNull(jobs.poll(), "A queued export must not start while the first job is active");

                    HttpResponse<String> overloaded = export(client, base, "monthly").get(10, TimeUnit.SECONDS);
                    assertEquals(503, overloaded.statusCode());
                    assertEquals("", overloaded.body());
                    assertNull(jobs.poll(), "Rejected work must not reach the export worker");

                    dailyJob.complete();
                    assertExport(daily.get(10, TimeUnit.SECONDS), "daily");
                    ExportJob weeklyJob = nextJob(jobs, "weekly");
                    weeklyJob.complete();
                    assertExport(weekly.get(10, TimeUnit.SECONDS), "weekly");

                    // Retrying the rejected report succeeds once the queued work has drained.
                    CompletableFuture<HttpResponse<String>> retried = export(client, base, "monthly");
                    nextJob(jobs, "monthly").complete();
                    assertExport(retried.get(10, TimeUnit.SECONDS), "monthly");
                } finally {
                    client.shutdownNow();
                }
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static CompletableFuture<HttpResponse<String>> export(HttpClient client, URI base, String period) {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/exports/" + period))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static ExportJob nextJob(BlockingQueue<ExportJob> jobs, String expectedPeriod)
            throws InterruptedException {
        ExportJob job = jobs.poll(10, TimeUnit.SECONDS);
        assertNotNull(job, "An admitted export must reach the worker");
        assertEquals(expectedPeriod, job.period());
        return job;
    }

    private static void awaitQueuedExport(QoSHandler admission) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        // Poll the public operational metric rather than guessing when the HTTP request arrives.
        while (admission.getSuspendedRequestCount() != 1 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertEquals(1, admission.getSuspendedRequestCount(), "The second export must wait for capacity");
    }

    private static void assertExport(HttpResponse<String> response, String period) {
        assertEquals(200, response.statusCode());
        assertEquals("text/csv;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow()
                .contains("filename=\"" + period + ".csv\""));
        assertEquals("period,total\n" + period + ",125.00\n", response.body());
    }

    private record ExportJob(String period, Response response, Callback callback) {
        private void complete() {
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/csv;charset=utf-8");
            response.getHeaders().put(HttpHeader.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + period + ".csv\"");
            Content.Sink.write(response, true, "period,total\n" + period + ",125.00\n", callback);
        }
    }

    private static class ReportHandler extends Handler.Abstract {
        private final BlockingQueue<ExportJob> jobs;

        private ReportHandler(BlockingQueue<ExportJob> jobs) {
            this.jobs = jobs;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            if (!"GET".equals(request.getMethod()) || !path.startsWith("/exports/")) {
                return false;
            }
            // Hand off to an asynchronous worker without blocking a Jetty request thread.
            jobs.add(new ExportJob(path.substring("/exports/".length()), response, callback));
            return true;
        }
    }
}
