/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_web_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.codec.SseEvent;
import io.vertx.ext.web.codec.spi.BodyStream;
import io.vertx.ext.web.common.WebEnvironment;
import io.vertx.ext.web.common.template.CachingTemplateEngine;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.common.template.impl.TemplateHolder;
import io.vertx.ext.web.multipart.FormDataPart;
import io.vertx.ext.web.multipart.MultipartForm;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Vertx_web_commonTest {
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    void bodyCodecsDecodeBufferedPayloads() throws Exception {
        Buffer jsonObject = Buffer.buffer("{\"name\":\"Ada\",\"score\":7}");
        Buffer jsonArray = Buffer.buffer("[\"one\",2,true]");

        assertThat(decode(BodyCodec.string(), Buffer.buffer("hello"))).isEqualTo("hello");
        assertThat(decode(BodyCodec.string("UTF-16"), Buffer.buffer("Gr\u00fc\u00dfe", "UTF-16"))).isEqualTo("Gr\u00fc\u00dfe");
        assertThat(decode(BodyCodec.buffer(), Buffer.buffer("payload")).toString()).isEqualTo("payload");
        assertThat(decode(BodyCodec.jsonObject(), jsonObject).getString("name")).isEqualTo("Ada");
        assertThat(decode(BodyCodec.jsonArray(), jsonArray)).containsExactly("one", 2, true);
        Map<?, ?> decodedMap = decode(BodyCodec.json(Map.class), jsonObject);
        assertThat(decodedMap.get("score")).isEqualTo(7);
        assertThat(decode(BodyCodec.none(), Buffer.buffer("discarded"))).isNull();

        BodyCodec<String> customCodec = BodyCodec.create((Buffer buffer) -> buffer.length() + ":" + buffer.toString());
        String customDecoded = decode(customCodec, Buffer.buffer("abc"));
        assertThat(customDecoded).isEqualTo("3:abc");
    }

    @Test
    void pipeBodyCodecWritesToTargetStreamAndHonorsCloseFlag() throws Exception {
        CollectingWriteStream keptOpen = new CollectingWriteStream();
        BodyStream<Void> openStream = createStream(BodyCodec.pipe(keptOpen, false));

        await(openStream.write(Buffer.buffer("one")));
        await(openStream.write(Buffer.buffer("two")));
        await(openStream.end());
        await(openStream.result());

        assertThat(keptOpen.content()).isEqualTo("onetwo");
        assertThat(keptOpen.closed()).isFalse();

        CollectingWriteStream closed = new CollectingWriteStream();
        BodyStream<Void> closingStream = createStream(BodyCodec.pipe(closed));

        await(closingStream.write(Buffer.buffer("done")));
        await(closingStream.end());
        await(closingStream.result());

        assertThat(closed.content()).isEqualTo("done");
        assertThat(closed.closed()).isTrue();
    }

    @Test
    void jsonStreamBodyCodecFeedsConfiguredParser() throws Exception {
        JsonParser parser = JsonParser.newParser().objectValueMode();
        List<JsonObject> values = new ArrayList<>();
        CompletableFuture<Void> ended = new CompletableFuture<>();
        parser.handler(event -> values.add(event.objectValue()));
        parser.endHandler(ignored -> ended.complete(null));

        BodyStream<Void> stream = createStream(BodyCodec.jsonStream(parser));
        await(stream.write(Buffer.buffer("{\"id\":1}")));
        await(stream.write(Buffer.buffer("{\"id\":2}")));
        await(stream.end());
        await(stream.result());
        ended.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(values).extracting(value -> value.getInteger("id")).containsExactly(1, 2);
    }

    @Test
    void sseStreamBodyCodecParsesEventsWithDemandControl() throws Exception {
        List<SseEvent> events = new ArrayList<>();
        AtomicReference<ReadStream<SseEvent>> readStream = new AtomicReference<>();
        CompletableFuture<Void> ended = new CompletableFuture<>();

        BodyStream<Void> stream = createStream(BodyCodec.sseStream(sseStream -> {
            sseStream.pause();
            sseStream.handler(events::add);
            sseStream.endHandler(ignored -> ended.complete(null));
            readStream.set(sseStream);
        }));

        await(stream.write(Buffer.buffer(": ignored comment\n"
                + "id: 42\n"
                + "event: update\n"
                + "data: line one\n"
                + "data: line two\n"
                + "retry: 1500\n"
                + "\n"
                + "id: 43\n"
                + "data: payload\n"
                + "\n")));

        assertThat(events).isEmpty();

        readStream.get().fetch(1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("42");
        assertThat(events.get(0).event()).isEqualTo("update");
        assertThat(events.get(0).data()).isEqualTo("line one\nline two");
        assertThat(events.get(0).retry()).isEqualTo(1500);

        readStream.get().fetch(1);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).event()).isEqualTo("message");
        assertThat(events.get(1).data()).isEqualTo("payload");

        readStream.get().resume();
        await(stream.end());
        await(stream.result());
        ended.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void sseEventConstructorsExposeDataObjectValues() {
        JsonObject json = new JsonObject()
                .put("id", "abc")
                .put("event", "created")
                .put("data", "payload")
                .put("retry", 250);

        SseEvent fromJson = new SseEvent(json);
        SseEvent copy = new SseEvent(fromJson);
        SseEvent direct = new SseEvent("abc", "created", "payload", 250);

        assertThat(fromJson).isEqualTo(direct);
        assertThat(copy.hashCode()).isEqualTo(direct.hashCode());
        assertThat(copy.toString()).contains("id: abc", "event: created", "payload", "retry: 250");

        SseEvent empty = new SseEvent();
        assertThat(empty.id()).isNull();
        assertThat(empty.event()).isNull();
        assertThat(empty.data()).isNull();
        assertThat(empty.retry()).isZero();
    }

    @Test
    void multipartFormRecordsAttributesUploadsAndCharset() {
        MultipartForm form = MultipartForm.create()
                .setCharset(StandardCharsets.ISO_8859_1)
                .attribute("description", "test file")
                .textFileUpload("notes", "notes.txt", "/tmp/notes.txt", "text/plain")
                .binaryFileUpload("image", "image.bin", Buffer.buffer(new byte[] {1, 2, 3}), "application/octet-stream")
                .setCharset("UTF-16");

        assertThat(form.getCharset()).isEqualTo(StandardCharsets.UTF_16);

        List<FormDataPart> parts = new ArrayList<>();
        form.forEach(parts::add);

        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).isAttribute()).isTrue();
        assertThat(parts.get(0).name()).isEqualTo("description");
        assertThat(parts.get(0).value()).isEqualTo("test file");
        assertThat(parts.get(0).isText()).isNull();

        assertThat(parts.get(1).isFileUpload()).isTrue();
        assertThat(parts.get(1).name()).isEqualTo("notes");
        assertThat(parts.get(1).filename()).isEqualTo("notes.txt");
        assertThat(parts.get(1).pathname()).isEqualTo("/tmp/notes.txt");
        assertThat(parts.get(1).mediaType()).isEqualTo("text/plain");
        assertThat(parts.get(1).isText()).isTrue();

        assertThat(parts.get(2).isFileUpload()).isTrue();
        assertThat(parts.get(2).filename()).isEqualTo("image.bin");
        assertThat(parts.get(2).content().getBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(parts.get(2).mediaType()).isEqualTo("application/octet-stream");
        assertThat(parts.get(2).isText()).isFalse();
    }

    @Test
    void multipartFormSupportsInMemoryTextAndPathBackedBinaryUploads() {
        MultipartForm form = MultipartForm.create()
                .textFileUpload("story", "story.txt", Buffer.buffer("Once upon a time"), "text/plain")
                .binaryFileUpload("archive", "archive.zip", "/tmp/archive.zip", "application/zip");

        List<FormDataPart> parts = new ArrayList<>();
        form.forEach(parts::add);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).isFileUpload()).isTrue();
        assertThat(parts.get(0).name()).isEqualTo("story");
        assertThat(parts.get(0).filename()).isEqualTo("story.txt");
        assertThat(parts.get(0).pathname()).isNull();
        assertThat(parts.get(0).content().toString()).isEqualTo("Once upon a time");
        assertThat(parts.get(0).mediaType()).isEqualTo("text/plain");
        assertThat(parts.get(0).isText()).isTrue();

        assertThat(parts.get(1).isFileUpload()).isTrue();
        assertThat(parts.get(1).name()).isEqualTo("archive");
        assertThat(parts.get(1).filename()).isEqualTo("archive.zip");
        assertThat(parts.get(1).pathname()).isEqualTo("/tmp/archive.zip");
        assertThat(parts.get(1).content()).isNull();
        assertThat(parts.get(1).mediaType()).isEqualTo("application/zip");
        assertThat(parts.get(1).isText()).isFalse();
    }

    @Test
    void templateEngineDefaultMethodsDelegateToMapRenderingAndFutures() throws Exception {
        RecordingTemplateEngine engine = new RecordingTemplateEngine();
        JsonObject context = new JsonObject().put("name", "Ada");

        Buffer renderedFromJson = await(engine.render(context, "hello"));
        Buffer renderedFromMap = await(engine.render(Map.of("name", "Grace"), "welcome"));

        assertThat(renderedFromJson.toString()).isEqualTo("hello:Ada");
        assertThat(renderedFromMap.toString()).isEqualTo("welcome:Grace");
        assertThat(engine.lastTemplate()).isEqualTo("welcome");
        assertThat(engine.lastContext()).containsEntry("name", "Grace");
        Object unwrapped = engine.unwrap();
        assertThat(unwrapped).isNull();

        engine.clearCache();
        assertThat(engine.clearCount()).isEqualTo(1);
    }

    @Test
    void cachingTemplateEngineStoresTemplatesInVertxLocalCache() throws Exception {
        String previousMode = System.getProperty(WebEnvironment.SYSTEM_PROPERTY_NAME);
        System.setProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, "prod");
        Vertx vertx = Vertx.vertx();
        try {
            TestCachingTemplateEngine engine = new TestCachingTemplateEngine(vertx, "tmpl");
            TemplateHolder<String> holder = new TemplateHolder<>("compiled-template", "views");

            assertThat(engine.adjust("views/home")).isEqualTo("views/home.tmpl");
            assertThat(engine.adjust("views/home.tmpl")).isEqualTo("views/home.tmpl");
            assertThat(engine.putTemplate("home", holder)).isNull();
            assertThat(engine.getTemplate("home").template()).isEqualTo("compiled-template");
            assertThat(engine.getTemplate("home").baseDir()).isEqualTo("views");

            engine.clearCache();
            assertThat(engine.getTemplate("home")).isNull();
        } finally {
            restoreSystemProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, previousMode);
            await(vertx.close());
        }
    }

    @Test
    void cachingTemplateEngineLeavesTemplateCacheDisabledInDevelopmentMode() throws Exception {
        String previousMode = System.getProperty(WebEnvironment.SYSTEM_PROPERTY_NAME);
        System.setProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, "dev");
        Vertx vertx = Vertx.vertx();
        try {
            TestCachingTemplateEngine engine = new TestCachingTemplateEngine(vertx, "tmpl");
            TemplateHolder<String> holder = new TemplateHolder<>("compiled-template");

            assertThat(engine.putTemplate("home", holder)).isNull();
            assertThat(engine.getTemplate("home")).isNull();
        } finally {
            restoreSystemProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, previousMode);
            await(vertx.close());
        }
    }

    @Test
    void webEnvironmentUsesSystemPropertyForModeAndDevelopmentFlag() {
        String previousMode = System.getProperty(WebEnvironment.SYSTEM_PROPERTY_NAME);
        try {
            System.setProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, "Development");
            assertThat(WebEnvironment.mode()).isEqualTo("Development");
            assertThat(WebEnvironment.development()).isTrue();

            System.setProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, "prod");
            assertThat(WebEnvironment.mode()).isEqualTo("prod");
            assertThat(WebEnvironment.development()).isFalse();
        } finally {
            restoreSystemProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, previousMode);
        }
    }

    private static <T> T decode(BodyCodec<T> codec, Buffer buffer) throws Exception {
        BodyStream<T> stream = createStream(codec);
        await(stream.write(buffer));
        await(stream.end());
        return await(stream.result());
    }

    private static <T> BodyStream<T> createStream(BodyCodec<T> codec) throws Exception {
        CompletableFuture<BodyStream<T>> future = new CompletableFuture<>();
        codec.create((Handler<AsyncResult<BodyStream<T>>>) result -> complete(future, result));
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> void complete(CompletableFuture<T> future, AsyncResult<T> result) {
        if (result.succeeded()) {
            future.complete(result.result());
        } else {
            future.completeExceptionally(result.cause());
        }
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class CollectingWriteStream implements WriteStream<Buffer> {
        private final Buffer content = Buffer.buffer();
        private boolean closed;

        @Override
        public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            content.appendBuffer(data);
            return Future.succeededFuture();
        }

        @Override
        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            content.appendBuffer(data);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public void end(Handler<AsyncResult<Void>> handler) {
            closed = true;
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
            return this;
        }

        String content() {
            return content.toString();
        }

        boolean closed() {
            return closed;
        }
    }

    private static final class RecordingTemplateEngine implements TemplateEngine {
        private final AtomicInteger clearCount = new AtomicInteger();
        private Map<String, Object> lastContext;
        private String lastTemplate;

        @Override
        public void render(
                Map<String, Object> context,
                String templateFileName,
                Handler<AsyncResult<Buffer>> handler) {
            lastContext = Map.copyOf(context);
            lastTemplate = templateFileName;
            Object name = Objects.requireNonNull(context.get("name"));
            handler.handle(Future.succeededFuture(Buffer.buffer(templateFileName + ":" + name)));
        }

        @Override
        public void clearCache() {
            clearCount.incrementAndGet();
        }

        Map<String, Object> lastContext() {
            return lastContext;
        }

        String lastTemplate() {
            return lastTemplate;
        }

        int clearCount() {
            return clearCount.get();
        }
    }

    private static final class TestCachingTemplateEngine extends CachingTemplateEngine<String> {
        private TestCachingTemplateEngine(Vertx vertx, String extension) {
            super(vertx, extension);
        }

        @Override
        public void render(
                Map<String, Object> context,
                String templateFileName,
                Handler<AsyncResult<Buffer>> handler) {
            handler.handle(Future.succeededFuture(Buffer.buffer(templateFileName)));
        }

        String adjust(String location) {
            return adjustLocation(location);
        }
    }
}
