/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoBodyOutputStreamTest {
    @Test
    void doHeadUsesNoBodyOutputStreamToMeasureResponseBodyLength() throws Exception {
        ContentLengthTrackingResponse response = new ContentLengthTrackingResponse();

        new HeadServlet().invokeDoHead(response);

        assertEquals(4, response.getRecordedContentLength());
    }

    static final class HeadServlet extends HttpServlet {
        void invokeDoHead(HttpServletResponse response) throws ServletException, IOException {
            doHead(null, response);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.write("body".getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    static final class ContentLengthTrackingResponse implements HttpServletResponse {
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private final ServletOutputStream outputStream = new RecordingServletOutputStream();
        private final PrintWriter writer = new PrintWriter(new StringWriter());
        private String characterEncoding = "ISO-8859-1";
        private String contentType;
        private int bufferSize;
        private Locale locale = Locale.getDefault();
        private int status = SC_OK;
        private boolean committed;
        private int recordedContentLength = -1;

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
        public void sendError(int sc, String msg) {
            status = sc;
            committed = true;
        }

        @Override
        public void sendError(int sc) {
            status = sc;
            committed = true;
        }

        @Override
        public void sendRedirect(String location) {
            status = SC_FOUND;
            committed = true;
            setHeader("Location", location);
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
            List<String> values = new ArrayList<>();
            values.add(value);
            headers.put(name, values);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
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
        public int getStatus() {
            return status;
        }

        @Override
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            List<String> values = headers.get(name);
            return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return Collections.unmodifiableSet(headers.keySet());
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
        public ServletOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        @Override
        public void setCharacterEncoding(String charset) {
            characterEncoding = charset;
        }

        @Override
        public void setContentLength(int len) {
            recordedContentLength = len;
            setIntHeader("Content-Length", len);
        }

        @Override
        public void setContentLengthLong(long len) {
            recordedContentLength = (int) len;
            setHeader("Content-Length", Long.toString(len));
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
        public void flushBuffer() {
            committed = true;
        }

        @Override
        public void resetBuffer() {
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public void reset() {
            headers.clear();
            status = SC_OK;
            committed = false;
            contentType = null;
            recordedContentLength = -1;
        }

        @Override
        public void setLocale(Locale loc) {
            locale = loc;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        int getRecordedContentLength() {
            return recordedContentLength;
        }
    }

    static final class RecordingServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int b) {
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }
}
