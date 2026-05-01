/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_fileupload2_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.fileupload2.core.AbstractFileUpload;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItemFactory.AbstractFileItemBuilder;
import org.apache.commons.fileupload2.core.FileItemHeaders;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadContentTypeException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadFileCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.core.MultipartInput;
import org.apache.commons.fileupload2.core.ParameterParser;
import org.apache.commons.fileupload2.core.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Commons_fileupload2_coreTest {
    private static final String CRLF = "\r\n";

    @TempDir
    Path tempDir;

    @Test
    void parsesMultipartRequestIntoDiskFileItemsWithHeadersAndProgress() throws Exception {
        String boundary = "AaB03x";
        String text = "R\u00e9sum\u00e9 caf\u00e9";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"description\"",
                        "Content-Type: text/plain; charset=UTF-8",
                        "X-Custom: first",
                        "X-Custom: second",
                        "",
                        text),
                part("Content-Disposition: form-data; name=\"upload\"; filename=\"notes.txt\"",
                        "Content-Type: text/plain",
                        "",
                        "alpha\nbeta"));
        Upload upload = newUpload(8);
        AtomicLong lastBytesRead = new AtomicLong();
        AtomicInteger lastItem = new AtomicInteger();
        upload.setProgressListener((bytesRead, contentLength, items) -> {
            lastBytesRead.set(bytesRead);
            lastItem.set(items);
        });

        List<DiskFileItem> items = upload.parseRequest(request(boundary, body));

        assertThat(items).hasSize(2);
        DiskFileItem description = items.get(0);
        assertThat(description.getFieldName()).isEqualTo("description");
        assertThat(description.isFormField()).isTrue();
        assertThat(description.getName()).isNull();
        assertThat(description.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(description.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(description.getString()).isEqualTo(text);
        assertThat(description.getHeaders().getHeader("x-custom")).isEqualTo("first");
        assertThat(iteratorToList(description.getHeaders().getHeaders("X-Custom"))).containsExactly("first", "second");

        DiskFileItem uploadItem = items.get(1);
        assertThat(uploadItem.getFieldName()).isEqualTo("upload");
        assertThat(uploadItem.isFormField()).isFalse();
        assertThat(uploadItem.getName()).isEqualTo("notes.txt");
        assertThat(uploadItem.getContentType()).isEqualTo("text/plain");
        assertThat(uploadItem.getString(StandardCharsets.US_ASCII)).isEqualTo("alpha\nbeta");
        assertThat(uploadItem.isInMemory()).isFalse();
        assertThat(uploadItem.getPath()).isNotNull();
        assertThat(Files.exists(uploadItem.getPath())).isTrue();
        assertThat(lastBytesRead.get()).isGreaterThan(0);
        assertThat(lastItem.get()).isEqualTo(2);
    }

    @Test
    void decodesRfc2231EncodedDispositionParameters() throws Exception {
        String boundary = "encoded-parameters";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name*=UTF-8''caf%C3%A9; filename*=UTF-8''r%C3%A9sum%C3%A9.txt",
                        "Content-Type: text/plain; charset=UTF-8",
                        "",
                        "contents"));
        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        List<DiskFileItem> items = upload.parseRequest(request(boundary, body));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getFieldName()).isEqualTo("caf\u00e9");
            assertThat(item.isFormField()).isFalse();
            assertThat(item.getName()).isEqualTo("r\u00e9sum\u00e9.txt");
            assertThat(item.getContentType()).isEqualTo("text/plain; charset=UTF-8");
            assertThat(item.getString()).isEqualTo("contents");
        });
    }

    @Test
    void groupsRepeatedFormFieldsInParameterMap() throws Exception {
        String boundary = "grouped";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"tag\"", "", "red"),
                part("Content-Disposition: form-data; name=\"tag\"", "", "blue"),
                part("Content-Disposition: form-data; name=\"single\"", "", "value"));
        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        Map<String, List<DiskFileItem>> parameterMap = upload.parseParameterMap(request(boundary, body));

        assertThat(parameterMap).containsOnlyKeys("tag", "single");
        assertThat(parameterMap.get("tag")).extracting(item -> item.getString()).containsExactly("red", "blue");
        assertThat(parameterMap.get("single")).singleElement().satisfies(item -> assertThat(item.getString()).isEqualTo("value"));
    }

    @Test
    void streamsNestedMultipartMixedFileItemsWithSharedFieldName() throws Exception {
        String boundary = "outer";
        String nestedBoundary = "inner";
        String nestedBody = "--" + nestedBoundary + CRLF
                + "Content-Disposition: attachment; filename=\"first.txt\"" + CRLF
                + "Content-Type: text/plain" + CRLF
                + CRLF
                + "first" + CRLF
                + "--" + nestedBoundary + CRLF
                + "Content-Disposition: attachment; filename=\"second.txt\"" + CRLF
                + "Content-Type: text/plain" + CRLF
                + CRLF
                + "second" + CRLF
                + "--" + nestedBoundary + "--";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"files\"",
                        "Content-Type: multipart/mixed; boundary=" + nestedBoundary,
                        "",
                        nestedBody));
        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        FileItemInputIterator iterator = upload.getItemIterator(request(boundary, body));

        assertThat(iterator.hasNext()).isTrue();
        FileItemInput first = iterator.next();
        assertThat(first.getFieldName()).isEqualTo("files");
        assertThat(first.isFormField()).isFalse();
        assertThat(first.getName()).isEqualTo("first.txt");
        assertThat(readUtf8(first.getInputStream())).isEqualTo("first");
        assertThat(iterator.hasNext()).isTrue();
        FileItemInput second = iterator.next();
        assertThat(second.getFieldName()).isEqualTo("files");
        assertThat(second.isFormField()).isFalse();
        assertThat(second.getName()).isEqualTo("second.txt");
        assertThat(readUtf8(second.getInputStream())).isEqualTo("second");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void streamingIteratorSkipsUnreadPartWhenAdvanced() throws Exception {
        String boundary = "skip-unread";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"first\"", "", "unread payload"),
                part("Content-Disposition: form-data; name=\"second\"", "", "read payload"));
        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        FileItemInputIterator iterator = upload.getItemIterator(request(boundary, body));

        assertThat(iterator.hasNext()).isTrue();
        FileItemInput first = iterator.next();
        assertThat(first.getFieldName()).isEqualTo("first");
        assertThat(iterator.hasNext()).isTrue();
        FileItemInput second = iterator.next();
        assertThat(second.getFieldName()).isEqualTo("second");
        assertThat(readUtf8(second.getInputStream())).isEqualTo("read payload");
        assertThatExceptionOfType(FileItemInput.ItemSkippedException.class)
                .isThrownBy(() -> first.getInputStream())
                .withMessageContaining("getInputStream()");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void enforcesRequestFileAndItemCountLimits() throws Exception {
        String boundary = "limits";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"first\"", "", "1"),
                part("Content-Disposition: form-data; name=\"second\"", "", "2"));
        Upload requestLimitedUpload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        requestLimitedUpload.setMaxSize(body.length - 1L);
        assertThatExceptionOfType(FileUploadSizeException.class)
                .isThrownBy(() -> requestLimitedUpload.parseRequest(request(boundary, body)))
                .satisfies(exception -> {
                    assertThat(exception.getPermitted()).isEqualTo(body.length - 1L);
                    assertThat(exception.getActualSize()).isEqualTo(body.length);
                });

        Upload fileLimitedUpload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        fileLimitedUpload.setMaxFileSize(3);
        byte[] largeBody = multipart(boundary,
                part("Content-Disposition: form-data; name=\"payload\"; filename=\"payload.txt\"",
                        "Content-Length: 4",
                        "",
                        "data"));
        assertThatExceptionOfType(FileUploadByteCountLimitException.class)
                .isThrownBy(() -> fileLimitedUpload.parseRequest(request(boundary, largeBody)))
                .satisfies(exception -> {
                    assertThat(exception.getFieldName()).isEqualTo("payload");
                    assertThat(exception.getFileName()).isEqualTo("payload.txt");
                    assertThat(exception.getPermitted()).isEqualTo(3);
                    assertThat(exception.getActualSize()).isEqualTo(4);
                });

        Upload countLimitedUpload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        countLimitedUpload.setMaxFileCount(1);
        assertThatExceptionOfType(FileUploadFileCountLimitException.class)
                .isThrownBy(() -> countLimitedUpload.parseRequest(request(boundary, body)))
                .satisfies(exception -> {
                    assertThat(exception.getPermitted()).isEqualTo(1);
                    assertThat(exception.getActualSize()).isEqualTo(1);
                });
    }

    @Test
    void rejectsNonMultipartAndMultipartWithoutBoundary() {
        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        assertThatExceptionOfType(FileUploadContentTypeException.class)
                .isThrownBy(() -> upload.getItemIterator(new MemoryRequestContext("text/plain", new byte[0], StandardCharsets.UTF_8)))
                .satisfies(exception -> assertThat(exception.getContentType()).isEqualTo("text/plain"));
        assertThatThrownBy(() -> upload.getItemIterator(new MemoryRequestContext("multipart/form-data", new byte[0], StandardCharsets.UTF_8)))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("no multipart boundary");
    }

    @Test
    void diskFileItemBuilderStoresSmallItemsInMemoryAndLargeItemsOnDisk() throws Exception {
        FileItemHeaders headers = AbstractFileItemBuilder.newFileItemHeaders();
        headers.addHeader("Content-Language", "en");
        DiskFileItem inMemory = DiskFileItem.builder()
                .setPath(tempDir)
                .setBufferSize(64)
                .setFieldName("comment")
                .setFileName("comment.txt")
                .setContentType("text/plain; charset=UTF-8")
                .setFormField(true)
                .setFileItemHeaders(headers)
                .get();
        try (OutputStream outputStream = inMemory.getOutputStream()) {
            outputStream.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(inMemory.isInMemory()).isTrue();
        assertThat(inMemory.getPath()).isNull();
        assertThat(inMemory.get()).containsExactly("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(inMemory.getString()).isEqualTo("hello");
        assertThat(inMemory.getHeaders().getHeader("content-language")).isEqualTo("en");
        inMemory.setFieldName("renamed").setFormField(false);
        assertThat(inMemory.getFieldName()).isEqualTo("renamed");
        assertThat(inMemory.isFormField()).isFalse();
        Path copied = tempDir.resolve("copied.txt");
        inMemory.write(copied);
        assertThat(Files.readString(copied)).isEqualTo("hello");

        DiskFileItem onDisk = DiskFileItem.builder()
                .setPath(tempDir)
                .setBufferSize(4)
                .setFieldName("upload")
                .setFileName("large.txt")
                .setContentType("application/octet-stream")
                .get();
        try (OutputStream outputStream = onDisk.getOutputStream()) {
            outputStream.write("0123456789".getBytes(StandardCharsets.US_ASCII));
        }
        Path tempFile = onDisk.getPath();
        assertThat(onDisk.isInMemory()).isFalse();
        assertThat(tempFile).isNotNull();
        assertThat(Files.exists(tempFile)).isTrue();
        assertThat(onDisk.getSize()).isEqualTo(10);
        assertThat(readUtf8(onDisk.getInputStream())).isEqualTo("0123456789");
        Path moved = tempDir.resolve("moved.bin");
        onDisk.write(moved);
        assertThat(Files.readString(moved, StandardCharsets.US_ASCII)).isEqualTo("0123456789");
        assertThat(onDisk.getSize()).isEqualTo(10);
        assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    void validatesFileNamesAndParsesHeaderParameters() {
        assertThat(DiskFileItem.checkFileName("safe-name.txt")).isEqualTo("safe-name.txt");
        assertThatExceptionOfType(InvalidPathException.class)
                .isThrownBy(() -> DiskFileItem.checkFileName("bad\0name.txt"))
                .satisfies(exception -> {
                    assertThat(exception.getInput()).isEqualTo("bad\0name.txt");
                    assertThat(exception.getIndex()).isEqualTo(3);
                    assertThat(exception.getReason()).contains("bad\\0name.txt");
                });

        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        Map<String, String> parameters = parser.parse("form-data; name=\"file\"; filename=\"a;b.txt\"; charset=UTF-8", ';');
        assertThat(parameters).containsEntry("name", "file");
        assertThat(parameters).containsEntry("filename", "a;b.txt");
        assertThat(parameters).containsEntry("charset", "UTF-8");

        Upload upload = newUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        assertThat(upload.getBoundary("multipart/form-data; boundary=\"abc123\"")).containsExactly("abc123".getBytes(StandardCharsets.ISO_8859_1));
        FileItemHeaders headers = upload.getParsedHeaders("Content-Disposition: form-data; name=\"field\"; filename=\"demo.txt\"" + CRLF
                + "X-Long: first" + CRLF
                + "\tcontinued" + CRLF
                + CRLF);
        assertThat(upload.getFieldName(headers)).isEqualTo("field");
        assertThat(upload.getFileName(headers)).isEqualTo("demo.txt");
        assertThat(headers.getHeader("x-long")).isEqualTo("first continued");
        assertThat(iteratorToList(headers.getHeaderNames())).containsExactly("content-disposition", "x-long");
        assertThat(AbstractFileUpload.isMultipartContent(request("abc", new byte[0]))).isTrue();
        assertThat(AbstractFileUpload.isMultipartContent(new MemoryRequestContext(null, new byte[0], StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void multipartInputReadsBodiesAndDiscardsUnneededParts() throws Exception {
        String boundary = "raw";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"keep\"", "", "kept"),
                part("Content-Disposition: form-data; name=\"drop\"", "", "discarded"));
        MultipartInput input = MultipartInput.builder()
                .setInputStream(new ByteArrayInputStream(body))
                .setBoundary(boundary.getBytes(StandardCharsets.ISO_8859_1))
                .get();

        assertThat(input.skipPreamble()).isTrue();
        assertThat(input.readHeaders()).contains("name=\"keep\"");
        assertThat(readUtf8(input.newInputStream())).isEqualTo("kept");
        assertThat(input.readBoundary()).isTrue();
        assertThat(input.readHeaders()).contains("name=\"drop\"");
        assertThat(input.discardBodyData()).isEqualTo("discarded".length());
        assertThat(input.readBoundary()).isFalse();
    }

    private Upload newUpload(final int threshold) {
        DiskFileItemFactory factory = DiskFileItemFactory.builder()
                .setPath(tempDir)
                .setBufferSize(threshold)
                .setCharset(StandardCharsets.ISO_8859_1)
                .get();
        Upload upload = new Upload();
        upload.setFileItemFactory(factory);
        return upload;
    }

    private static MemoryRequestContext request(final String boundary, final byte[] body) {
        return new MemoryRequestContext("multipart/form-data; boundary=" + boundary, body, StandardCharsets.UTF_8);
    }

    private static byte[] multipart(final String boundary, final String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append("--").append(boundary).append(CRLF).append(part).append(CRLF);
        }
        builder.append("--").append(boundary).append("--").append(CRLF);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String part(final String... lines) {
        return String.join(CRLF, lines);
    }

    private static String readUtf8(final InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> iteratorToList(final Iterator<String> iterator) {
        List<String> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static final class Upload extends AbstractFileUpload<MemoryRequestContext, DiskFileItem, DiskFileItemFactory> {
        @Override
        public FileItemInputIterator getItemIterator(final MemoryRequestContext request) throws FileUploadException, IOException {
            return super.getItemIterator((RequestContext) request);
        }

        @Override
        public Map<String, List<DiskFileItem>> parseParameterMap(final MemoryRequestContext request) throws FileUploadException {
            return super.parseParameterMap((RequestContext) request);
        }

        @Override
        public List<DiskFileItem> parseRequest(final MemoryRequestContext request) throws FileUploadException {
            return super.parseRequest((RequestContext) request);
        }
    }

    private static final class MemoryRequestContext implements RequestContext {
        private final String contentType;
        private final byte[] body;
        private final Charset charset;

        private MemoryRequestContext(final String contentType, final byte[] body, final Charset charset) {
            this.contentType = contentType;
            this.body = body.clone();
            this.charset = charset;
        }

        @Override
        public String getCharacterEncoding() {
            return charset.name();
        }

        @Override
        public long getContentLength() {
            return body.length;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public boolean isMultipartRelated() {
            return contentType != null
                    && contentType.trim().regionMatches(true, 0, "multipart/related", 0, "multipart/related".length());
        }
    }
}
