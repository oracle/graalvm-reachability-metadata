/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.BufferedSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Streaming request bodies send meter readings with chunked transfer encoding and no precomputed length.
 * An energy gateway serializes readings directly into the request sink instead of building
 * an entire encoded batch in memory, then receives the service's ingestion receipt.
 */
public class MeterReadingStreamUploadTest {

    @Test
    @Timeout(60)
    public void streamsReadingsAsNewlineDelimitedJsonAndReceivesReceipt() throws Exception {
        List<MeterReading> readings = List.of(
                new MeterReading(101, 4250),
                new MeterReading(102, 4315),
                new MeterReading(103, 4400));
        try (MockWebServer ingestion = new MockWebServer()) {
            ingestion.enqueue(new MockResponse()
                    .setResponseCode(202)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Location", "/ingestions/batch-42")
                    .setBody("{\"accepted\":3,\"lastSequence\":103}"));
            ingestion.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request upload = new Request.Builder()
                        .url(ingestion.url("/meters/north-depot/readings"))
                        .header("Idempotency-Key", "north-depot-101-103")
                        .post(new ReadingBatch(readings))
                        .build();
                try (Response response = client.newCall(upload).execute()) {
                    assertThat(response.code()).isEqualTo(202);
                    assertThat(response.header("Location")).isEqualTo("/ingestions/batch-42");
                    assertThat(response.body().string()).isEqualTo("{\"accepted\":3,\"lastSequence\":103}");
                }

                RecordedRequest received = ingestion.takeRequest(10, TimeUnit.SECONDS);
                assertThat(received).isNotNull();
                assertThat(received.getMethod()).isEqualTo("POST");
                assertThat(received.getPath()).isEqualTo("/meters/north-depot/readings");
                assertThat(received.getHeader("Idempotency-Key")).isEqualTo("north-depot-101-103");
                assertThat(received.getHeader("Content-Type")).isEqualTo("application/x-ndjson; charset=utf-8");
                assertThat(received.getHeader("Transfer-Encoding")).isEqualTo("chunked");
                assertThat(received.getHeader("Content-Length")).isNull();
                assertThat(received.getBody().readUtf8()).isEqualTo("""
                        {"sequence":101,"wattHours":4250}
                        {"sequence":102,"wattHours":4315}
                        {"sequence":103,"wattHours":4400}
                        """);
                assertThat(ingestion.getRequestCount()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private record MeterReading(long sequence, long wattHours) {
    }

    private static final class ReadingBatch extends RequestBody {
        private final Iterable<MeterReading> readings;

        private ReadingBatch(Iterable<MeterReading> readings) {
            this.readings = readings;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse("application/x-ndjson; charset=utf-8");
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            for (MeterReading reading : readings) {
                sink.writeUtf8("{\"sequence\":")
                        .writeDecimalLong(reading.sequence())
                        .writeUtf8(",\"wattHours\":")
                        .writeDecimalLong(reading.wattHours())
                        .writeUtf8("}\n");
            }
        }
    }
}
