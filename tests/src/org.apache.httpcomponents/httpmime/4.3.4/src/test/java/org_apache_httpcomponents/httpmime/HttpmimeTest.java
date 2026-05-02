/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpmime;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MinimalField;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class HttpmimeTest {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final Charset US_ASCII = StandardCharsets.US_ASCII;

    @TempDir
    Path tempDir;

    @Test
    void stringBodyExposesTextContentAndDescriptors() throws Exception {
        String text = "Plain body with accented text: caf\u00e9";
        StringBody body = new StringBody(text, textContentType(UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        body.writeTo(outputStream);

        assertThat(body.getMimeType()).isEqualTo("text/plain");
        assertThat(body.getMediaType()).isEqualTo("text");
        assertThat(body.getSubType()).isEqualTo("plain");
        assertThat(body.getCharset()).isEqualTo("UTF-8");
        assertThat(body.getTransferEncoding()).isEqualTo(MIME.ENC_8BIT);
        assertThat(body.getFilename()).isNull();
        assertThat(body.getContentLength()).isEqualTo(text.getBytes(UTF_8).length);
        assertThat(outputStream.toByteArray()).isEqualTo(text.getBytes(UTF_8));
        assertThat(readAll(body.getReader())).isEqualTo(text);
    }

    @Test
    void stringBodyAppliesProvidedContentTypeAndAsciiDefaultEncoding() throws Exception {
        String defaultText = "factory generated text";
        String unicodeText = "factory generated snowman: \u2603";
        StringBody defaultBody = new StringBody(defaultText, ContentType.create("text/plain"));
        StringBody utf8Body = new StringBody(unicodeText, textContentType(UTF_8));
        ByteArrayOutputStream defaultOutputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream utf8OutputStream = new ByteArrayOutputStream();

        defaultBody.writeTo(defaultOutputStream);
        utf8Body.writeTo(utf8OutputStream);

        assertThat(defaultBody.getMimeType()).isEqualTo("text/plain");
        assertThat(defaultBody.getMediaType()).isEqualTo("text");
        assertThat(defaultBody.getSubType()).isEqualTo("plain");
        assertThat(defaultBody.getCharset()).isNull();
        assertThat(defaultBody.getContentLength()).isEqualTo(defaultText.getBytes(US_ASCII).length);
        assertThat(defaultOutputStream.toByteArray()).isEqualTo(defaultText.getBytes(US_ASCII));
        assertThat(readAll(defaultBody.getReader())).isEqualTo(defaultText);
        assertThat(utf8Body.getMimeType()).isEqualTo("text/plain");
        assertThat(utf8Body.getCharset()).isEqualTo("UTF-8");
        assertThat(utf8Body.getContentLength()).isEqualTo(unicodeText.getBytes(UTF_8).length);
        assertThat(utf8OutputStream.toByteArray()).isEqualTo(unicodeText.getBytes(UTF_8));
    }

    @Test
    void binaryContentBodiesStreamBytesAndExposeMetadata() throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5};
        ByteArrayBody byteArrayBody = new ByteArrayBody(bytes, ContentType.APPLICATION_OCTET_STREAM, "bytes.bin");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byteArrayBody.writeTo(byteArrayOutputStream);

        assertThat(byteArrayBody.getFilename()).isEqualTo("bytes.bin");
        assertThat(byteArrayBody.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(byteArrayBody.getMediaType()).isEqualTo("application");
        assertThat(byteArrayBody.getSubType()).isEqualTo("octet-stream");
        assertThat(byteArrayBody.getCharset()).isNull();
        assertThat(byteArrayBody.getTransferEncoding()).isEqualTo(MIME.ENC_BINARY);
        assertThat(byteArrayBody.getContentLength()).isEqualTo(bytes.length);
        assertThat(byteArrayOutputStream.toByteArray()).isEqualTo(bytes);

        byte[] streamBytes = "streamed content".getBytes(UTF_8);
        InputStreamBody inputStreamBody = new InputStreamBody(
                new ByteArrayInputStream(streamBytes),
                textContentType(null),
                "stream.txt");
        ByteArrayOutputStream inputStreamOutputStream = new ByteArrayOutputStream();

        inputStreamBody.writeTo(inputStreamOutputStream);

        assertThat(inputStreamBody.getFilename()).isEqualTo("stream.txt");
        assertThat(inputStreamBody.getMimeType()).isEqualTo("text/plain");
        assertThat(inputStreamBody.getContentLength()).isEqualTo(-1);
        assertThat(inputStreamBody.getCharset()).isNull();
        assertThat(inputStreamBody.getTransferEncoding()).isEqualTo(MIME.ENC_BINARY);
        assertThat(inputStreamOutputStream.toByteArray()).isEqualTo(streamBytes);
    }

    @Test
    void fileBodyUsesProvidedNameCharsetAndFileContent() throws Exception {
        byte[] fileBytes = "file body payload".getBytes(UTF_8);
        Path file = tempDir.resolve("source-file.txt");
        Files.write(file, fileBytes);
        FileBody body = new FileBody(file.toFile(), textContentType(UTF_8), "upload-name.txt");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        body.writeTo(outputStream);

        assertThat(body.getFile()).isEqualTo(file.toFile());
        assertThat(body.getFilename()).isEqualTo("upload-name.txt");
        assertThat(body.getMimeType()).isEqualTo("text/plain");
        assertThat(body.getCharset()).isEqualTo("UTF-8");
        assertThat(body.getTransferEncoding()).isEqualTo(MIME.ENC_BINARY);
        assertThat(body.getContentLength()).isEqualTo(fileBytes.length);
        assertThat(outputStream.toByteArray()).isEqualTo(fileBytes);
    }

    @Test
    void formBodyPartCreatesStandardHeadersAndSupportsCaseInsensitiveLookup() throws Exception {
        Path file = tempDir.resolve("report.txt");
        Files.write(file, "report".getBytes(UTF_8));
        FileBody body = new FileBody(file.toFile(), textContentType(UTF_8), "report.txt");
        FormBodyPart part = new FormBodyPart("upload", body);

        part.addField("X-Custom", "first");
        part.addField("x-custom", "second");

        org.apache.http.entity.mime.Header header = part.getHeader();

        assertThat(part.getName()).isEqualTo("upload");
        assertThat(part.getBody()).isSameAs(body);
        assertThat(header.getField(MIME.CONTENT_DISPOSITION).getBody())
                .isEqualTo("form-data; name=\"upload\"; filename=\"report.txt\"");
        assertThat(header.getField(MIME.CONTENT_TYPE).getBody())
                .isEqualTo("text/plain; charset=UTF-8");
        assertThat(header.getField(MIME.CONTENT_TRANSFER_ENC).getBody()).isEqualTo("binary");
        assertThat(header.getField("content-type").getName()).isEqualTo(MIME.CONTENT_TYPE);
        assertThat(header.getFields("X-CUSTOM"))
                .extracting(MinimalField::getBody)
                .containsExactly("first", "second");
        assertThat(iteratedFieldNames(header))
                .containsExactly(
                        MIME.CONTENT_DISPOSITION,
                        MIME.CONTENT_TYPE,
                        MIME.CONTENT_TRANSFER_ENC,
                        "X-Custom",
                        "x-custom");
        assertThat(header.removeFields("x-custom")).isEqualTo(2);
        assertThat(header.getFields("X-Custom")).isEmpty();
    }

    @Test
    void strictMultipartWritesAllHeadersAndKnownTotalLength() throws Exception {
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.STRICT)
                .setCharset(UTF_8)
                .setBoundary("strict-boundary")
                .addPart("comment", new StringBody("hello", textContentType(UTF_8)))
                .addPart("file", new ByteArrayBody(
                        "abc".getBytes(UTF_8),
                        ContentType.APPLICATION_OCTET_STREAM,
                        "a.bin"))
                .build();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);
        String multipartText = outputStream.toString(UTF_8.name());

        String expected = String.join("\r\n",
                "--strict-boundary",
                "Content-Disposition: form-data; name=\"comment\"",
                "Content-Type: text/plain; charset=UTF-8",
                "Content-Transfer-Encoding: 8bit",
                "",
                "hello",
                "--strict-boundary",
                "Content-Disposition: form-data; name=\"file\"; filename=\"a.bin\"",
                "Content-Type: application/octet-stream",
                "Content-Transfer-Encoding: binary",
                "",
                "abc",
                "--strict-boundary--",
                "");
        assertThat(entity.getContentType().getValue())
                .isEqualTo("multipart/form-data; boundary=strict-boundary; charset=UTF-8");
        assertThat(multipartText).isEqualTo(expected);
        assertThat(entity.getContentLength()).isEqualTo(outputStream.size());
    }

    @Test
    void browserCompatibleMultipartOmitsNonBrowserHeaders() throws Exception {
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setCharset(UTF_8)
                .setBoundary("browser-boundary")
                .addPart("description", new StringBody("visible text", textContentType(UTF_8)))
                .addPart("file", new ByteArrayBody(
                        "PNG".getBytes(UTF_8),
                        ContentType.create("image/png"),
                        "image.png"))
                .build();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);
        String multipartText = outputStream.toString(UTF_8.name());

        String expected = String.join("\r\n",
                "--browser-boundary",
                "Content-Disposition: form-data; name=\"description\"",
                "",
                "visible text",
                "--browser-boundary",
                "Content-Disposition: form-data; name=\"file\"; filename=\"image.png\"",
                "Content-Type: image/png",
                "",
                "PNG",
                "--browser-boundary--",
                "");
        assertThat(multipartText).isEqualTo(expected);
        assertThat(multipartText).doesNotContain("Content-Transfer-Encoding");
    }

    @Test
    void multipartEntityReportsRepeatableStateAndContentLength() throws Exception {
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.STRICT)
                .setCharset(UTF_8)
                .setBoundary("entity-boundary")
                .addPart("message", new StringBody("hello entity", textContentType(UTF_8)))
                .addPart("data", new ByteArrayBody(
                        "123".getBytes(UTF_8),
                        ContentType.APPLICATION_OCTET_STREAM,
                        "data.bin"))
                .build();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        Header contentType = entity.getContentType();
        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isChunked()).isFalse();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentEncoding()).isNull();
        assertThat(contentType.getName()).isEqualTo("Content-Type");
        assertThat(contentType.getValue())
                .isEqualTo("multipart/form-data; boundary=entity-boundary; charset=UTF-8");
        assertThat(entity.getContentLength()).isEqualTo(outputStream.size());
        assertThat(outputStream.toString(UTF_8.name())).contains("hello entity", "123");
    }

    @Test
    void multipartEntityWithUnknownLengthBodyIsStreamingButStillWritable() throws Exception {
        byte[] streamedBytes = "stream-only".getBytes(UTF_8);
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setCharset(UTF_8)
                .setBoundary("stream-boundary")
                .addPart("stream", new InputStreamBody(
                        new ByteArrayInputStream(streamedBytes),
                        ContentType.APPLICATION_OCTET_STREAM,
                        "stream.bin"))
                .build();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThat(entity.isRepeatable()).isFalse();
        assertThat(entity.isChunked()).isTrue();
        assertThat(entity.isStreaming()).isTrue();
        assertThat(entity.getContentLength()).isEqualTo(-1);

        entity.writeTo(outputStream);

        assertThat(outputStream.toString(UTF_8.name())).contains("stream-only");
    }

    @Test
    void defaultMultipartEntityGeneratesBoundaryAndUsesItWhenWriting() throws Exception {
        HttpEntity entity = MultipartEntityBuilder.create()
                .addPart("field", new StringBody("auto-boundary", textContentType(UTF_8)))
                .build();
        Header contentType = entity.getContentType();
        String boundaryParameter = "boundary=";
        String contentTypeValue = contentType.getValue();
        int boundaryStart = contentTypeValue.indexOf(boundaryParameter) + boundaryParameter.length();
        String boundary = contentTypeValue.substring(boundaryStart);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);
        String multipartText = outputStream.toString(UTF_8.name());

        assertThat(contentType.getName()).isEqualTo("Content-Type");
        assertThat(contentTypeValue).startsWith("multipart/form-data; boundary=");
        assertThat(contentTypeValue).doesNotContain("charset=");
        assertThat(boundary).isNotEmpty().matches("[-_0-9A-Za-z]+");
        assertThat(multipartText).startsWith("--" + boundary + "\r\n");
        assertThat(multipartText).contains("Content-Disposition: form-data; name=\"field\"", "auto-boundary");
        assertThat(multipartText).endsWith("--" + boundary + "--\r\n");
    }

    @Test
    void constructorsRejectInvalidArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new StringBody(null, textContentType(UTF_8)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ByteArrayBody(null, ContentType.APPLICATION_OCTET_STREAM, "missing.bin"))
                .withMessage("byte[] may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FileBody((File) null))
                .withMessage("File may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new InputStreamBody(null, ContentType.APPLICATION_OCTET_STREAM, "missing.bin"))
                .withMessage("Input stream may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FormBodyPart(null, new StringBody("value", textContentType(UTF_8))))
                .withMessage("Name may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FormBodyPart("field", null))
                .withMessage("Body may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MultipartEntityBuilder.create()
                        .addPart(null, new StringBody("value", textContentType(UTF_8))))
                .withMessage("Name may not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MultipartEntityBuilder.create().addPart("field", null))
                .withMessage("Content body may not be null");
    }

    private static ContentType textContentType(Charset charset) {
        return ContentType.create("text/plain", charset);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[256];
        int length;
        while ((length = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, length);
        }
        return builder.toString();
    }

    private static List<String> iteratedFieldNames(org.apache.http.entity.mime.Header header) {
        List<String> names = new ArrayList<>();
        for (MinimalField field : header) {
            names.add(field.getName());
        }
        return names;
    }
}
