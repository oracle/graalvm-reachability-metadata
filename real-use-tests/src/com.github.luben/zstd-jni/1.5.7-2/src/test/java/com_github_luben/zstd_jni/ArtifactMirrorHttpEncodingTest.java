/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serves and accepts HTTP bodies with `Content-Encoding: zstd`, where Netty's
 * `HttpContentCompressor` and `HttpContentDecompressor` run on zstd-jni and the clients encode
 * and decode with zstd-jni's `ZstdOutputStream`, `ZstdInputStream` and `Zstd` frame inspection.
 *
 * An artifact mirror publishes its catalog as JSON. Build agents that send
 * `Accept-Encoding: zstd` receive the catalog as a stream of zstd frames, one per block the
 * server buffers, and older agents that only accept gzip receive it uncompressed. Agents upload
 * their build reports zstd-compressed with a checksum, and the mirror inflates them before its
 * handlers see them.
 */
class ArtifactMirrorHttpEncodingTest {

    private static final int LEVEL = 6;
    private static final int BLOCK_SIZE = 32 * 1024;
    private static final int MAX_ENCODE_SIZE = 4 * 1024 * 1024;
    private static final long UNKNOWN_CONTENT_SIZE = -1L;
    private static final byte[] ZSTD_MAGIC = {(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD};

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void catalogIsServedAsZstdFramesToAgentsThatAcceptIt() throws IOException {
        List<Artifact> catalog = catalog(2_000);
        byte[] json = mapper.writeValueAsBytes(catalog);
        assertThat(json.length).isGreaterThan(3 * BLOCK_SIZE);

        Exchange exchange = serveCatalog("gzip;q=0.5, zstd", json);

        assertThat(exchange.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                .isEqualTo(HttpHeaderValues.ZSTD.toString());
        byte[] body = exchange.body();
        assertThat(body.length).isLessThan(json.length / 3);
        assertThat(body).startsWith(ZSTD_MAGIC);
        // The mirror flushes one frame per full block, each declaring its content size.
        assertThat(Zstd.getFrameContentSize(body)).isEqualTo(BLOCK_SIZE);

        byte[] restored;
        try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(body))) {
            restored = in.readAllBytes();
        }
        assertThat(restored).isEqualTo(json);
        List<Artifact> received = mapper.readValue(restored, new TypeReference<List<Artifact>>() { });
        assertThat(received).isEqualTo(catalog);
    }

    @Test
    void agentsWithoutZstdReceiveTheCatalogUncompressed() throws IOException {
        byte[] json = mapper.writeValueAsBytes(catalog(200));

        Exchange exchange = serveCatalog("gzip", json);

        assertThat(exchange.headers().contains(HttpHeaderNames.CONTENT_ENCODING)).isFalse();
        assertThat(exchange.body()).isEqualTo(json);
    }

    @Test
    void zstdUploadIsInflatedBeforeTheHandlerSeesIt() throws IOException {
        BuildReport report = buildReport();
        byte[] json = mapper.writeValueAsBytes(report);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZstdOutputStream out = new ZstdOutputStream(compressed, LEVEL)) {
            out.setChecksum(true);
            out.write(json);
        }
        byte[] upload = compressed.toByteArray();
        assertThat(upload.length).isLessThan(json.length / 3);
        // A streamed frame leaves its size unknown, so the mirror has to inflate it incrementally.
        assertThat(Zstd.getFrameContentSize(upload)).isEqualTo(UNKNOWN_CONTENT_SIZE);

        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/reports", Unpooled.wrappedBuffer(upload));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        request.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.ZSTD);
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, upload.length);

        EmbeddedChannel mirror = new EmbeddedChannel(new HttpContentDecompressor());
        try {
            assertThat(mirror.writeInbound(request)).isTrue();
            Exchange received = drain(mirror, false);

            assertThat(received.headers().contains(HttpHeaderNames.CONTENT_ENCODING)).isFalse();
            assertThat(received.body()).isEqualTo(json);
            assertThat(mapper.readValue(received.body(), BuildReport.class)).isEqualTo(report);
        } finally {
            mirror.finishAndReleaseAll();
        }
    }

    /// Sends one catalog request through the mirror's compressor and returns what the agent gets.
    private Exchange serveCatalog(String acceptEncoding, byte[] json) {
        EmbeddedChannel mirror = new EmbeddedChannel(new HttpContentCompressor(0,
                StandardCompressionOptions.zstd(LEVEL, BLOCK_SIZE, MAX_ENCODE_SIZE)));
        try {
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "/catalog.json");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, acceptEncoding);
            assertThat(mirror.writeInbound(request)).isTrue();
            ReferenceCountUtil.release(mirror.readInbound());

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK, Unpooled.wrappedBuffer(json));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, json.length);
            assertThat(mirror.writeOutbound(response)).isTrue();
            return drain(mirror, true);
        } finally {
            mirror.finishAndReleaseAll();
        }
    }

    /// Collects the head and the body bytes of the one message waiting on a side of `channel`.
    private static Exchange drain(EmbeddedChannel channel, boolean outbound) {
        HttpMessage head = null;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean last = false;
        while (!last) {
            Object message = outbound ? channel.readOutbound() : channel.readInbound();
            assertThat(message).as("message after %d body bytes", body.size()).isNotNull();
            try {
                if (message instanceof HttpMessage httpMessage) {
                    head = httpMessage;
                }
                if (message instanceof HttpContent content) {
                    ByteBuf buffer = content.content();
                    byte[] chunk = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), chunk);
                    body.writeBytes(chunk);
                    last = content instanceof LastHttpContent;
                }
            } finally {
                ReferenceCountUtil.release(message);
            }
        }
        assertThat(head).isNotNull();
        return new Exchange(head, body.toByteArray());
    }

    private static List<Artifact> catalog(int size) {
        List<Artifact> artifacts = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String group = "org.example.module" + (i % 37);
            String name = "component-" + i;
            String version = "1." + (i % 12) + "." + (i % 5);
            artifacts.add(new Artifact(group, name, version,
                    "/" + group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + ".jar",
                    String.format("%064x", (long) i * 0x9E3779B97F4A7C15L & Long.MAX_VALUE),
                    10_000L + i * 731L));
        }
        return artifacts;
    }

    private static BuildReport buildReport() {
        List<String> testCases = new ArrayList<>();
        for (int i = 0; i < 1_500; i++) {
            testCases.add("org.example.service.Module" + (i % 40) + "Test#scenario" + i + ": PASSED");
        }
        return new BuildReport("agent-17", "build-20261019-0042", 1_500, 0, testCases);
    }

    record Artifact(String group, String name, String version, String path, String sha256, long size) {
    }

    record BuildReport(String agent, String build, int passed, int failed, List<String> testCases) {
    }

    record Exchange(HttpMessage head, byte[] body) {

        HttpHeaders headers() {
            return head.headers();
        }
    }
}
