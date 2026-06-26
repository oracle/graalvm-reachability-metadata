/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_mongo_3_1;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

public class MongoDbAttributesGetterTest {
    private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey(
            "db.query.text");
    private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");

    @Test
    void commandListenerRecordsSanitizedMongoCommand() {
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            CommandListener listener = MongoTelemetry.builder(openTelemetry)
                    .setMaxNormalizedQueryLength(64)
                    .build()
                    .createCommandListener();
            ServerAddress serverAddress = new ServerAddress("localhost", 27017);
            ServerId serverId = new ServerId(new ClusterId("mongo-test"), serverAddress);
            ConnectionDescription connectionDescription = new ConnectionDescription(serverId);
            BsonArray answers = new BsonArray(List.of(new BsonInt32(42), new BsonString("tea")));
            BsonDocument filter = new BsonDocument()
                    .append("name", new BsonString("Arthur Dent"))
                    .append("answers", answers);
            BsonDocument command = new BsonDocument()
                    .append("find", new BsonString("travelers"))
                    .append("filter", filter);

            CommandStartedEvent started = new CommandStartedEvent(
                    17, connectionDescription, "guide", "find", command);
            BsonDocument response = new BsonDocument("ok", new BsonInt32(1));
            CommandSucceededEvent succeeded = new CommandSucceededEvent(
                    17, connectionDescription, "find", response, 1_000_000);
            listener.commandStarted(started);
            listener.commandSucceeded(succeeded);

            assertThat(tracerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(spanExporter.getExportedSpans())
                    .singleElement()
                    .satisfies(span -> {
                        assertThat(span.getName()).isEqualTo("find guide.travelers");
                        assertThat(queryText(span))
                                .contains("travelers")
                                .contains("?")
                                .doesNotContain("Arthur Dent")
                                .hasSizeLessThanOrEqualTo(64);
                    });
        } finally {
            assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            openTelemetry.close();
        }
    }

    private static String queryText(SpanData span) {
        String oldSemconvQueryText = span.getAttributes().get(DB_STATEMENT);
        if (oldSemconvQueryText != null) {
            return oldSemconvQueryText;
        }
        return span.getAttributes().get(DB_QUERY_TEXT);
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> exportedSpans = Collections.synchronizedList(
                new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exportedSpans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private List<SpanData> getExportedSpans() {
            return List.copyOf(exportedSpans);
        }
    }
}
