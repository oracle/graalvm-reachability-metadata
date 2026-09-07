/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty_contrib.netty_codec_multipart_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.contrib.multipart.ContentDisposition;
import io.netty.contrib.multipart.DecoderQuirk;
import io.netty.contrib.multipart.FormDecoderException;
import io.netty.contrib.multipart.ParsedHeaderValue;
import io.netty.contrib.multipart.PostBodyDecoder;
import io.netty.contrib.multipart.TooManyFormFieldsException;
import io.netty.contrib.multipart.UndecodedDataLimitExceededException;
import io.netty.contrib.multipart.VintageAccess;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;

public class Netty_codec_multipart_coreTest {
    @Test
    void decodesFragmentedUrlEncodedFieldsAndPercentEscapes() {
        FormCollector collector = new FormCollector();

        try (PostBodyDecoder decoder = PostBodyDecoder.builder()
                .charset(StandardCharsets.UTF_8)
                .forUrlEncodedData()) {
            decoder.add(utf8("first=hello+world&na"));
            collector.drain(decoder);
            decoder.add(utf8("me=Netty%2"));
            collector.drain(decoder);
            decoder.add(utf8("0Core&empty=&currency=%E2%82%AC"));
            decoder.endInput();
            collector.drain(decoder);
        }

        collector.assertComplete();
        assertThat(collector.fields).hasSize(4);
        assertField(collector.fields.get(0), "first", null, "hello world");
        assertField(collector.fields.get(1), "name", null, "Netty Core");
        assertField(collector.fields.get(2), "empty", null, "");
        assertField(collector.fields.get(3), "currency", null, "€");
        for (DecodedField field : collector.fields) {
            assertThat(field.headerNames).containsExactly(HttpHeaderNames.CONTENT_DISPOSITION.toString());
            assertThat(field.headers).isEmpty();
        }
        assertThat(collector.events).containsExactly(
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADER,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.FIELD_COMPLETE,
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADER,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.FIELD_COMPLETE,
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADER,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.FIELD_COMPLETE,
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADER,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.FIELD_COMPLETE);
    }

    @Test
    void preservesMalformedPercentEscapesByDefault() {
        FormCollector collector = new FormCollector();

        try (PostBodyDecoder decoder = PostBodyDecoder.builder().forUrlEncodedData()) {
            decoder.add(utf8("field=%GG%2"));
            decoder.endInput();
            collector.drain(decoder);
        }

        collector.assertComplete();
        assertThat(collector.fields).hasSize(1);
        assertField(collector.fields.get(0), "field", null, "%GG%2");
    }

    @Test
    void rejectsMalformedPercentEscapesWhenCompatibilityQuirksAreEnabled() {
        assertMalformedPercentEscapeRejected(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE, "field=%GG");
        assertMalformedPercentEscapeRejected(DecoderQuirk.REFUSE_SHORT_PERCENT_DECODE, "field=%2");
    }

    @Test
    void decodesMultipartHeadersExtendedFilenameAndChunkedContent() {
        String boundary = "Boundary-7MA4YWxk";
        FormCollector collector = new FormCollector();

        try (PostBodyDecoder decoder = PostBodyDecoder.builder()
                .charset(StandardCharsets.UTF_8)
                .compactionThreshold(32)
                .forMultipartBoundary(boundary)) {
            decoder.add(utf8("--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"description\"\r\nContent-Ty"));
            collector.drain(decoder);
            decoder.add(utf8("pe: text/plain; charset=UTF-8\r\n\r\nhello "));
            collector.drain(decoder);
            decoder.add(utf8("multipart\r\n--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"upload\"; "
                    + "filename*=UTF-8''caf%C3%A9.txt\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Content-Transfer-Encoding: binary\r\n\r\nbinary-"));
            collector.drain(decoder);
            decoder.add(utf8("content\r\n--" + boundary + "--\r\n"));
            decoder.endInput();
            collector.drain(decoder);
        }

        collector.assertComplete();
        assertThat(collector.fields).hasSize(2);
        DecodedField description = collector.fields.get(0);
        assertField(description, "description", null, "hello multipart");
        assertThat(description.headers)
                .containsEntry(HttpHeaderNames.CONTENT_DISPOSITION.toString(),
                        "form-data; name=\"description\"")
                .containsEntry(HttpHeaderNames.CONTENT_TYPE.toString(), "text/plain; charset=UTF-8");

        DecodedField upload = collector.fields.get(1);
        assertField(upload, "upload", "café.txt", "binary-content");
        assertThat(upload.headers)
                .containsEntry(HttpHeaderNames.CONTENT_TYPE.toString(), "application/octet-stream")
                .containsEntry(HttpHeaderNames.CONTENT_TRANSFER_ENCODING.toString(), "binary");
        assertThat(collector.events).contains(PostBodyDecoder.Event.CONTENT);
        assertThat(collector.events).doesNotContain(PostBodyDecoder.Event.BEGIN_MIXED);
    }

    @Test
    void decodesFilesInsideMultipartMixedField() {
        String outerBoundary = "outer-boundary";
        String body = """
                --outer-boundary
                Content-Disposition: form-data; name="files"
                Content-Type: multipart/mixed; boundary="inner-boundary"

                --inner-boundary
                Content-Disposition: attachment; filename="alpha.txt"
                Content-Type: text/plain

                alpha
                --inner-boundary
                Content-Disposition: attachment; filename="beta.txt"
                Content-Type: text/plain

                beta
                --inner-boundary--
                --outer-boundary--
                """.replace("\n", "\r\n");
        List<PostBodyDecoder.Event> events = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        String outerFieldName = null;
        String currentFileName = null;
        StringBuilder currentContent = null;
        boolean mixed = false;

        try (PostBodyDecoder decoder = PostBodyDecoder.builder()
                .charset(StandardCharsets.UTF_8)
                .forMultipartBoundary(outerBoundary)) {
            decoder.add(utf8(body));
            decoder.endInput();

            PostBodyDecoder.Event event;
            while ((event = decoder.next()) != null) {
                events.add(event);
                switch (event) {
                    case BEGIN_FIELD:
                        if (mixed) {
                            currentContent = new StringBuilder();
                        }
                        break;
                    case HEADER:
                        ParsedHeaderValue header = decoder.parsedHeaderValue();
                        if (header instanceof ContentDisposition) {
                            ContentDisposition disposition = (ContentDisposition) header;
                            if (mixed) {
                                currentFileName = disposition.fileName();
                            } else {
                                outerFieldName = disposition.name();
                            }
                        }
                        break;
                    case BEGIN_MIXED:
                        mixed = true;
                        break;
                    case CONTENT:
                        assertThat(currentContent).isNotNull();
                        currentContent.append(decoder.decodedContentString());
                        break;
                    case FIELD_COMPLETE:
                        if (currentContent != null) {
                            fileNames.add(currentFileName);
                            contents.add(currentContent.toString());
                            currentFileName = null;
                            currentContent = null;
                        } else {
                            mixed = false;
                        }
                        break;
                    case HEADERS_COMPLETE:
                        assertThat(currentContent).isNotNull();
                        break;
                    default:
                        throw new AssertionError("Unexpected decoder event: " + event);
                }
            }
        }

        assertThat(outerFieldName).isEqualTo("files");
        assertThat(fileNames).containsExactly("alpha.txt", "beta.txt");
        assertThat(contents).containsExactly("alpha", "beta");
        assertThat(events).containsSubsequence(
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.BEGIN_MIXED,
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.FIELD_COMPLETE,
                PostBodyDecoder.Event.BEGIN_FIELD,
                PostBodyDecoder.Event.HEADERS_COMPLETE,
                PostBodyDecoder.Event.CONTENT,
                PostBodyDecoder.Event.FIELD_COMPLETE,
                PostBodyDecoder.Event.FIELD_COMPLETE);
    }

    @Test
    void enforcesConfiguredFieldAndUndecodedDataLimits() {
        FormCollector collector = new FormCollector();
        try (PostBodyDecoder decoder = PostBodyDecoder.builder().maxFields(1).forUrlEncodedData()) {
            decoder.add(utf8("first=one&second=two"));
            decoder.endInput();

            assertThatExceptionOfType(TooManyFormFieldsException.class)
                    .isThrownBy(() -> collector.drain(decoder));
        }
        assertThat(collector.fields).hasSize(1);
        assertField(collector.fields.get(0), "first", null, "one");

        ByteBuf buffered = utf8("pending");
        ByteBuf rejected = utf8("more");
        try (PostBodyDecoder decoder = PostBodyDecoder.builder()
                .undecodedLimit(3)
                .forUrlEncodedData()) {
            decoder.add(buffered);
            assertThatExceptionOfType(UndecodedDataLimitExceededException.class)
                    .isThrownBy(() -> decoder.add(rejected));
            assertThat(rejected.refCnt()).isZero();
        }
        assertThat(buffered.refCnt()).isZero();
    }

    @Test
    void exposesCompatibilityControlsThroughVintageAccess() {
        PostBodyDecoder.Builder builder = PostBodyDecoder.builder()
                .charset(StandardCharsets.UTF_8)
                .compactionThreshold(64)
                .maxFields(7)
                .enableAllQuirks()
                .disableQuirks(DecoderQuirk.EARLY_DECODE);

        assertThat(VintageAccess.maxFields(builder)).isEqualTo(7);
        assertThat(VintageAccess.cleanString("  form-data;\tname=\"upload\"  "))
                .isEqualTo("form-data  name upload");

        try (VintageAccess.MultipartDecoder decoder =
                VintageAccess.forBoundaryWithPrefix(builder, "--vintage-boundary")) {
            assertThat(decoder.getCharset()).isEqualTo(StandardCharsets.UTF_8);
            assertThat(decoder.getCompactionThreshold()).isEqualTo(64);
            assertThat(decoder.hasQuirk(DecoderQuirk.LEGACY_HEADER_SPLITTING)).isTrue();
            assertThat(decoder.hasQuirk(DecoderQuirk.EARLY_DECODE)).isFalse();
            decoder.setCompactionThreshold(128);
            assertThat(decoder.getCompactionThreshold()).isEqualTo(128);
        }

        try (PostBodyDecoder decoder = PostBodyDecoder.builder().forUrlEncodedData()) {
            VintageAccess.UrlEncodedDecoder vintageDecoder = (VintageAccess.UrlEncodedDecoder) decoder;
            ByteBuf component = utf8("Netty+Core%21");
            try {
                vintageDecoder.decodeComponent(component, false);
                assertThat(component.toString(StandardCharsets.UTF_8)).isEqualTo("Netty Core!");
            } finally {
                component.release();
            }
            assertThat(vintageDecoder.isEof()).isFalse();
            decoder.endInput();
            assertThat(vintageDecoder.isEof()).isTrue();
        }
    }

    private static ByteBuf utf8(String value) {
        return Unpooled.copiedBuffer(value, StandardCharsets.UTF_8);
    }

    private static void assertMalformedPercentEscapeRejected(DecoderQuirk quirk, String body) {
        FormCollector collector = new FormCollector();
        try (PostBodyDecoder decoder = PostBodyDecoder.builder()
                .enableQuirks(quirk)
                .forUrlEncodedData()) {
            decoder.add(utf8(body));
            decoder.endInput();

            assertThatExceptionOfType(FormDecoderException.class)
                    .isThrownBy(() -> collector.drain(decoder));
        }
    }

    private static void assertField(DecodedField field, String name, String fileName, String content) {
        assertThat(field.name).isEqualTo(name);
        assertThat(field.fileName).isEqualTo(fileName);
        assertThat(field.content).hasToString(content);
    }

    private static final class FormCollector {
        private final List<DecodedField> fields = new ArrayList<>();
        private final List<PostBodyDecoder.Event> events = new ArrayList<>();
        private DecodedField current;

        void drain(PostBodyDecoder decoder) {
            PostBodyDecoder.Event event;
            while ((event = decoder.next()) != null) {
                events.add(event);
                switch (event) {
                    case BEGIN_FIELD:
                        assertThat(current).isNull();
                        current = new DecodedField();
                        break;
                    case HEADER:
                        readHeader(decoder);
                        break;
                    case HEADERS_COMPLETE:
                        assertThat(current).isNotNull();
                        break;
                    case CONTENT:
                        assertThat(current).isNotNull();
                        current.content.append(decoder.decodedContentString());
                        break;
                    case FIELD_COMPLETE:
                        assertThat(current).isNotNull();
                        fields.add(current);
                        current = null;
                        break;
                    case BEGIN_MIXED:
                        assertThat(current).isNotNull();
                        break;
                    default:
                        throw new AssertionError("Unexpected decoder event: " + event);
                }
            }
        }

        void assertComplete() {
            assertThat(current).isNull();
        }

        private void readHeader(PostBodyDecoder decoder) {
            assertThat(current).isNotNull();
            String headerName = decoder.headerName().toString().toLowerCase(Locale.ROOT);
            current.headerNames.add(headerName);
            if (decoder.hasUnparsedHeaderValue()) {
                current.headers.put(headerName, decoder.headerValue());
            }
            ParsedHeaderValue parsedHeaderValue = decoder.parsedHeaderValue();
            if (parsedHeaderValue instanceof ContentDisposition) {
                ContentDisposition disposition = (ContentDisposition) parsedHeaderValue;
                current.name = disposition.name();
                current.fileName = disposition.fileName();
            }
        }
    }

    private static final class DecodedField {
        private final List<String> headerNames = new ArrayList<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final StringBuilder content = new StringBuilder();
        private String name;
        private String fileName;
    }
}
