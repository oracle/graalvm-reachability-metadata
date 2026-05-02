/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_fileupload.commons_fileupload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileCountLimitExceededException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.InvalidFileNameException;
import org.apache.commons.fileupload.ParameterParser;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.UploadContext;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.util.FileItemHeadersImpl;
import org.apache.commons.fileupload.util.Streams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Commons_fileuploadTest {
    private static final String BOUNDARY = "AaB03x";

    @TempDir
    Path tempDirectory;

    @Test
    void parsesMultipartRequestIntoFormFieldsFilesHeadersAndParameterMap() throws Exception {
        byte[] requestBody = multipartBody(
                part(
                        "Content-Disposition: form-data; name=\"description\"\r\n"
                                + "Content-Type: text/plain; charset=UTF-8\r\n"
                                + "X-Trace: first\r\n"
                                + "X-Trace: second\r\n",
                        "R\u00e9sum\u00e9 upload"),
                part(
                        "Content-Disposition: form-data; name=\"document\"; filename=\"notes.txt\"\r\n"
                                + "Content-Type: text/plain\r\n",
                        "line 1\nline 2"));
        DiskFileItemFactory factory = new DiskFileItemFactory(DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD,
                tempDirectory.toFile());
        FileUpload upload = new FileUpload(factory);
        upload.setHeaderEncoding(StandardCharsets.UTF_8.name());

        List<FileItem> items = upload.parseRequest(context(requestBody));

        assertThat(items).hasSize(2);
        FileItem description = items.get(0);
        assertThat(description.isFormField()).isTrue();
        assertThat(description.getFieldName()).isEqualTo("description");
        assertThat(description.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(description.getString(StandardCharsets.UTF_8.name())).isEqualTo("R\u00e9sum\u00e9 upload");
        assertThat(headers(description.getHeaders().getHeaders("x-trace"))).containsExactly("first", "second");

        FileItem document = items.get(1);
        assertThat(document.isFormField()).isFalse();
        assertThat(document.getFieldName()).isEqualTo("document");
        assertThat(document.getName()).isEqualTo("notes.txt");
        assertThat(document.getContentType()).isEqualTo("text/plain");
        assertThat(document.get()).isEqualTo("line 1\nline 2".getBytes(StandardCharsets.UTF_8));

        Map<String, List<FileItem>> parameterMap = upload.parseParameterMap(context(requestBody));
        assertThat(parameterMap).containsOnlyKeys("description", "document");
        assertThat(parameterMap.get("description")).singleElement().satisfies(item -> {
            assertThat(item.isFormField()).isTrue();
            assertThat(item.getString(StandardCharsets.UTF_8.name())).isEqualTo("R\u00e9sum\u00e9 upload");
        });
        assertThat(parameterMap.get("document")).singleElement().satisfies(item -> {
            assertThat(item.isFormField()).isFalse();
            assertThat(item.getName()).isEqualTo("notes.txt");
        });
    }

    @Test
    void streamsMultipartItemsSequentiallyWithoutMaterializingFileItems() throws Exception {
        byte[] requestBody = multipartBody(
                part("Content-Disposition: form-data; name=\"alpha\"\r\n", "one"),
                part(
                        "Content-Disposition: form-data; name=\"payload\"; filename=\"payload.bin\"\r\n"
                                + "Content-Type: application/octet-stream\r\n",
                        "0123456789"));
        FileUpload upload = new FileUpload(new DiskFileItemFactory());

        FileItemIterator iterator = upload.getItemIterator(context(requestBody));

        assertThat(iterator.hasNext()).isTrue();
        FileItemStream alpha = iterator.next();
        assertThat(alpha.isFormField()).isTrue();
        assertThat(alpha.getFieldName()).isEqualTo("alpha");
        assertThat(Streams.asString(alpha.openStream(), StandardCharsets.UTF_8.name())).isEqualTo("one");

        assertThat(iterator.hasNext()).isTrue();
        FileItemStream payload = iterator.next();
        assertThat(payload.isFormField()).isFalse();
        assertThat(payload.getFieldName()).isEqualTo("payload");
        assertThat(payload.getName()).isEqualTo("payload.bin");
        assertThat(payload.getContentType()).isEqualTo("application/octet-stream");
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        assertThat(Streams.copy(payload.openStream(), copied, true)).isEqualTo(10L);
        assertThat(copied.toByteArray()).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void reportsUploadProgressWithContentLengthAndCurrentItemIndex() throws Exception {
        byte[] requestBody = multipartBody(
                part("Content-Disposition: form-data; name=\"first\"\r\n", "alpha"),
                part("Content-Disposition: form-data; name=\"second\"; filename=\"second.txt\"\r\n", "bravo"));
        FileUpload upload = new FileUpload(new DiskFileItemFactory());
        List<ProgressUpdate> updates = new ArrayList<>();
        ProgressListener listener = (bytesRead, contentLength, itemIndex) -> updates.add(
                new ProgressUpdate(bytesRead, contentLength, itemIndex));
        upload.setProgressListener(listener);

        assertThat(upload.parseRequest(context(requestBody))).hasSize(2);

        assertThat(upload.getProgressListener()).isSameAs(listener);
        assertThat(updates).isNotEmpty();
        long previousBytesRead = -1L;
        for (ProgressUpdate update : updates) {
            assertThat(update.contentLength).isEqualTo((long) requestBody.length);
            assertThat(update.bytesRead).isGreaterThanOrEqualTo(previousBytesRead)
                    .isLessThanOrEqualTo((long) requestBody.length);
            previousBytesRead = update.bytesRead;
        }
        assertThat(updates).extracting(update -> update.itemIndex).contains(1, 2);
        ProgressUpdate finalUpdate = updates.get(updates.size() - 1);
        assertThat(finalUpdate.bytesRead).isEqualTo((long) requestBody.length);
        assertThat(finalUpdate.itemIndex).isEqualTo(2);
    }

    @Test
    void spillsLargeItemsToRepositoryAndWritesThemToDestinationFile() throws Exception {
        DiskFileItemFactory factory = new DiskFileItemFactory(8, tempDirectory.toFile());
        FileItem item = factory.createItem("file", "text/plain", false, "large.txt");
        byte[] content = "content larger than the in-memory threshold".getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = item.getOutputStream()) {
            output.write(content);
        }

        assertThat(item).isInstanceOf(DiskFileItem.class);
        DiskFileItem diskItem = (DiskFileItem) item;
        assertThat(diskItem.isInMemory()).isFalse();
        assertThat(diskItem.getStoreLocation()).exists().isFile();
        assertThat(item.getInputStream()).hasBinaryContent(content);

        File destination = tempDirectory.resolve("written-large.txt").toFile();
        item.write(destination);
        assertThat(destination).hasBinaryContent(content);

        item.delete();
        assertThat(diskItem.getStoreLocation()).doesNotExist();
    }

    @Test
    void keepsSmallItemsInMemoryAndAppliesConfiguredDefaultCharset() throws Exception {
        DiskFileItemFactory factory = new DiskFileItemFactory(1024, tempDirectory.toFile());
        factory.setDefaultCharset(StandardCharsets.UTF_8.name());
        FileItem item = factory.createItem("comment", "text/plain", true, null);
        try (OutputStream output = item.getOutputStream()) {
            output.write("Gr\u00fc\u00dfe".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(item.isInMemory()).isTrue();
        assertThat(item.isFormField()).isTrue();
        assertThat(item.getFieldName()).isEqualTo("comment");
        assertThat(item.getName()).isNull();
        assertThat(item.getString()).isEqualTo("Gr\u00fc\u00dfe");

        item.setFieldName("renamed");
        item.setFormField(false);
        assertThat(item.getFieldName()).isEqualTo("renamed");
        assertThat(item.isFormField()).isFalse();
    }

    @Test
    void decodesItemTextUsingCharsetDeclaredInContentType() throws Exception {
        DiskFileItemFactory factory = new DiskFileItemFactory(1024, tempDirectory.toFile());
        factory.setDefaultCharset(StandardCharsets.UTF_8.name());
        FileItem item = factory.createItem("message", "text/plain; charset=ISO-8859-1", true, null);
        try (OutputStream output = item.getOutputStream()) {
            output.write("ol\u00e1".getBytes(StandardCharsets.ISO_8859_1));
        }

        assertThat(item).isInstanceOf(DiskFileItem.class);
        DiskFileItem diskItem = (DiskFileItem) item;
        assertThat(diskItem.getCharSet()).isEqualTo(StandardCharsets.ISO_8859_1.name());
        assertThat(item.getString()).isEqualTo("ol\u00e1");
    }

    @Test
    void rejectsRequestsExceedingConfiguredTotalFileAndFileCountLimits() throws Exception {
        byte[] oneLargeFile = multipartBody(part(
                "Content-Disposition: form-data; name=\"file\"; filename=\"large.txt\"\r\n"
                        + "Content-Type: text/plain\r\n",
                "1234567890"));
        FileUpload fileSizeLimited = new FileUpload(new DiskFileItemFactory());
        fileSizeLimited.setFileSizeMax(4);
        assertThatThrownBy(() -> fileSizeLimited.parseRequest(context(oneLargeFile)))
                .isInstanceOf(FileUploadBase.FileSizeLimitExceededException.class)
                .satisfies(throwable -> {
                    FileUploadBase.FileSizeLimitExceededException exception =
                            (FileUploadBase.FileSizeLimitExceededException) throwable;
                    assertThat(exception.getPermittedSize()).isEqualTo(4L);
                    assertThat(exception.getActualSize()).isGreaterThan(4L);
                    assertThat(exception.getFieldName()).isEqualTo("file");
                    assertThat(exception.getFileName()).isEqualTo("large.txt");
                });

        FileUpload requestSizeLimited = new FileUpload(new DiskFileItemFactory());
        requestSizeLimited.setSizeMax(oneLargeFile.length - 1L);
        assertThatThrownBy(() -> requestSizeLimited.parseRequest(context(oneLargeFile)))
                .isInstanceOf(FileUploadBase.SizeLimitExceededException.class)
                .satisfies(throwable -> {
                    FileUploadBase.SizeLimitExceededException exception =
                            (FileUploadBase.SizeLimitExceededException) throwable;
                    assertThat(exception.getPermittedSize()).isEqualTo(oneLargeFile.length - 1L);
                    assertThat(exception.getActualSize()).isEqualTo((long) oneLargeFile.length);
                });

        byte[] twoFiles = multipartBody(
                part("Content-Disposition: form-data; name=\"first\"; filename=\"a.txt\"\r\n", "a"),
                part("Content-Disposition: form-data; name=\"second\"; filename=\"b.txt\"\r\n", "b"));
        FileUpload countLimited = new FileUpload(new DiskFileItemFactory());
        countLimited.setFileCountMax(1);
        assertThatThrownBy(() -> countLimited.parseRequest(context(twoFiles)))
                .isInstanceOf(FileCountLimitExceededException.class)
                .satisfies(throwable -> assertThat(((FileCountLimitExceededException) throwable).getLimit())
                        .isEqualTo(1L));
    }

    @Test
    void detectsMultipartRequestsAndRejectsInvalidContentTypes() {
        byte[] emptyBody = new byte[0];
        assertThat(FileUploadBase.isMultipartContent(context(emptyBody))).isTrue();
        assertThat(FileUploadBase.isMultipartContent(
                new ByteArrayUploadContext(emptyBody, "text/plain", StandardCharsets.UTF_8.name()))).isFalse();

        FileUpload upload = new FileUpload(new DiskFileItemFactory());
        assertThatThrownBy(() -> upload.parseRequest(
                new ByteArrayUploadContext(emptyBody, "text/plain", StandardCharsets.UTF_8.name())))
                .isInstanceOf(FileUploadBase.InvalidContentTypeException.class)
                .hasMessageContaining("multipart");
    }

    @Test
    void parsesHeaderParametersAndStoresCaseInsensitiveMultiValueHeaders() {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);

        Map<String, String> parameters = parser.parse(
                "form-data; Name=\"Upload\"; filename=\"notes.txt\"; empty=", ';');

        assertThat(parameters).containsEntry("form-data", null)
                .containsEntry("name", "Upload")
                .containsEntry("filename", "notes.txt")
                .containsEntry("empty", null);

        FileItemHeadersImpl headers = new FileItemHeadersImpl();
        headers.addHeader("Content-Type", "text/plain");
        headers.addHeader("X-Request-Id", "one");
        headers.addHeader("x-request-id", "two");

        assertThat(headers.getHeader("content-type")).isEqualTo("text/plain");
        assertThat(headers(headers.getHeaders("X-REQUEST-ID"))).containsExactly("one", "two");
        assertThat(headerNames(headers.getHeaderNames())).contains("content-type", "x-request-id");
    }

    @Test
    void streamUtilitiesCopyDecodeAndRejectInvalidFileNames() throws Exception {
        byte[] utf8 = "Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144".getBytes(StandardCharsets.UTF_8);
        assertThat(Streams.asString(new ByteArrayInputStream(utf8), StandardCharsets.UTF_8.name()))
                .isEqualTo("Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(Streams.copy(new ByteArrayInputStream(utf8), output, true, new byte[3]))
                .isEqualTo((long) utf8.length);
        assertThat(output.toByteArray()).isEqualTo(utf8);

        assertThat(Streams.checkFileName("safe-name.txt")).isEqualTo("safe-name.txt");
        assertThatThrownBy(() -> Streams.checkFileName("bad\0name.txt"))
                .isInstanceOf(InvalidFileNameException.class)
                .satisfies(throwable -> assertThat(((InvalidFileNameException) throwable).getName())
                        .isEqualTo("bad\0name.txt"));
    }

    private static ByteArrayUploadContext context(byte[] body) {
        return new ByteArrayUploadContext(
                body, "multipart/form-data; boundary=" + BOUNDARY, StandardCharsets.UTF_8.name());
    }

    private static byte[] multipartBody(String... parts) {
        StringBuilder body = new StringBuilder();
        for (String part : parts) {
            body.append("--").append(BOUNDARY).append("\r\n");
            body.append(part);
        }
        body.append("--").append(BOUNDARY).append("--\r\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String part(String headers, String body) {
        return headers + "\r\n" + body + "\r\n";
    }

    private static List<String> headers(Iterator<String> iterator) {
        List<String> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static List<String> headerNames(Iterator<String> iterator) {
        return headers(iterator);
    }

    private static final class ProgressUpdate {
        private final long bytesRead;
        private final long contentLength;
        private final int itemIndex;

        private ProgressUpdate(long bytesRead, long contentLength, int itemIndex) {
            this.bytesRead = bytesRead;
            this.contentLength = contentLength;
            this.itemIndex = itemIndex;
        }
    }

    private static final class ByteArrayUploadContext implements UploadContext {
        private final byte[] body;
        private final String contentType;
        private final String characterEncoding;

        private ByteArrayUploadContext(byte[] body, String contentType, String characterEncoding) {
            this.body = body.clone();
            this.contentType = contentType;
            this.characterEncoding = characterEncoding;
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public long contentLength() {
            return body.length;
        }
    }
}
