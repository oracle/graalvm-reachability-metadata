/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Call cancellation abandons a pending export without disabling the application's HTTP client.
 * A reporting screen cancels an obsolete download after changing its selected period,
 * observes failure through the callback, and downloads the newly selected report.
 */
public class ExportCallCancellationTest {

    @Test
    @Timeout(60)
    public void cancelsObsoleteExportAndDownloadsNewSelection() throws Exception {
        try (MockWebServer reports = new MockWebServer()) {
            // Keep the export pending until the user cancels, rather than racing a delayed reply.
            reports.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            reports.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/csv; charset=utf-8")
                    .setBody("period,total\ncurrent,42.50\n"));
            reports.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request oldSelection = new Request.Builder()
                        .url(reports.url("/exports?period=previous"))
                        .build();
                Call obsolete = client.newCall(oldSelection);
                ExportCallback pending = new ExportCallback();
                obsolete.enqueue(pending);

                // Receipt proves the call is in flight before cancellation; no sleep is needed.
                RecordedRequest abandoned = reports.takeRequest(10, TimeUnit.SECONDS);
                assertThat(abandoned).isNotNull();
                assertThat(abandoned.getMethod()).isEqualTo("GET");
                assertThat(abandoned.getPath()).isEqualTo("/exports?period=previous");
                obsolete.cancel();

                assertThatThrownBy(() -> pending.content.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(IOException.class);
                assertThat(obsolete.isCanceled()).isTrue();

                Request newSelection = oldSelection.newBuilder()
                        .url(reports.url("/exports?period=current"))
                        .build();
                ExportCallback selected = new ExportCallback();
                Call replacement = client.newCall(newSelection);
                replacement.enqueue(selected);
                assertThat(selected.content.get(10, TimeUnit.SECONDS))
                        .isEqualTo("period,total\ncurrent,42.50\n");
                assertThat(replacement.isCanceled()).isFalse();

                RecordedRequest downloaded = reports.takeRequest(10, TimeUnit.SECONDS);
                assertThat(downloaded).isNotNull();
                assertThat(downloaded.getMethod()).isEqualTo("GET");
                assertThat(downloaded.getPath()).isEqualTo("/exports?period=current");
                assertThat(reports.getRequestCount()).isEqualTo(2);
            } finally {
                client.dispatcher().cancelAll();
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static final class ExportCallback implements Callback {
        private final CompletableFuture<String> content = new CompletableFuture<>();

        @Override
        public void onFailure(Call call, IOException failure) {
            content.completeExceptionally(failure);
        }

        @Override
        public void onResponse(Call call, Response response) {
            try (response) {
                if (!response.isSuccessful()) {
                    throw new IOException("Export service returned HTTP " + response.code());
                }
                content.complete(response.body().string());
            } catch (IOException failure) {
                content.completeExceptionally(failure);
            }
        }
    }
}
