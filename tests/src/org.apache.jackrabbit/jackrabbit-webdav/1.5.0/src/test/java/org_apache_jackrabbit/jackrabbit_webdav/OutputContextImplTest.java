/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.OutputContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OutputContextImplTest {
    @Test
    void writesEntityMetadataToServletResponse() throws IOException {
        RecordingHttpServletResponse response = new RecordingHttpServletResponse();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputContextImpl context = new OutputContextImpl(response, outputStream);

        context.getOutputStream().write("body".getBytes("UTF-8"));
        context.setContentLanguage("en-US");
        context.setContentLength(4L);
        context.setContentType("text/plain");
        context.setModificationTime(123456789L);
        context.setETag("\"etag-1\"");
        context.setProperty("X-Jackrabbit-Trace", "trace-1");

        assertThat(context.hasStream()).isTrue();
        assertThat(context.getOutputStream()).isSameAs(outputStream);
        assertThat(outputStream.toString("UTF-8")).isEqualTo("body");
        assertThat(response.headers)
                .containsEntry(DavConstants.HEADER_CONTENT_LANGUAGE, "en-US")
                .containsEntry(DavConstants.HEADER_LAST_MODIFIED, "123456789")
                .containsEntry(DavConstants.HEADER_ETAG, "\"etag-1\"")
                .containsEntry("X-Jackrabbit-Trace", "trace-1");
        assertThat(response.contentLength).isEqualTo(4);
        assertThat(response.contentType).isEqualTo("text/plain");
    }

    @Test
    void ignoresMissingOptionalValuesAndAbsentStream() {
        RecordingHttpServletResponse response = new RecordingHttpServletResponse();
        OutputContextImpl context = new OutputContextImpl(response, null);

        context.setContentLanguage(null);
        context.setContentLength(-1L);
        context.setContentType(null);
        context.setModificationTime(-1L);
        context.setETag(null);
        context.setProperty(null, "trace-1");
        context.setProperty("X-Jackrabbit-Trace", null);

        assertThat(context.hasStream()).isFalse();
        assertThat(context.getOutputStream()).isNull();
        assertThat(response.headers).isEmpty();
        assertThat(response.contentLength).isZero();
        assertThat(response.contentType).isNull();
    }

    @Test
    void rejectsMissingServletResponse() {
        assertThatThrownBy(() -> new OutputContextImpl(null, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Response must not be null");
    }

    private static final class RecordingHttpServletResponse implements HttpServletResponse {
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private int status;
        private String characterEncoding = "UTF-8";
        private String contentType;
        private int contentLength;
        private int bufferSize;
        private boolean committed;
        private Locale locale = Locale.ROOT;

        @Override
        public void addCookie(Cookie cookie) {
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        public String encodeUrl(String url) {
            return url;
        }

        @Override
        public String encodeRedirectUrl(String url) {
            return url;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            status = sc;
            committed = true;
        }

        @Override
        public void sendError(int sc) throws IOException {
            status = sc;
            committed = true;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            setHeader("Location", location);
            committed = true;
        }

        @Override
        public void setDateHeader(String name, long date) {
            setHeader(name, Long.toString(date));
        }

        @Override
        public void addDateHeader(String name, long date) {
            addHeader(name, Long.toString(date));
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            setHeader(name, Integer.toString(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            addHeader(name, Integer.toString(value));
        }

        @Override
        public void setStatus(int sc) {
            status = sc;
        }

        @Override
        public void setStatus(int sc, String sm) {
            status = sc;
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new RecordingServletOutputStream(body);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            return new PrintWriter(body, true);
        }

        @Override
        public void setCharacterEncoding(String charset) {
            characterEncoding = charset;
        }

        @Override
        public void setContentLength(int len) {
            contentLength = len;
        }

        @Override
        public void setContentType(String type) {
            contentType = type;
        }

        @Override
        public void setBufferSize(int size) {
            bufferSize = size;
        }

        @Override
        public int getBufferSize() {
            return bufferSize;
        }

        @Override
        public void flushBuffer() throws IOException {
            committed = true;
        }

        @Override
        public void resetBuffer() {
            body.reset();
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public void reset() {
            headers.clear();
            body.reset();
            contentLength = 0;
            contentType = null;
            status = 0;
        }

        @Override
        public void setLocale(Locale loc) {
            locale = loc;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }
    }

    private static final class RecordingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream outputStream;

        private RecordingServletOutputStream(ByteArrayOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
        }
    }
}
