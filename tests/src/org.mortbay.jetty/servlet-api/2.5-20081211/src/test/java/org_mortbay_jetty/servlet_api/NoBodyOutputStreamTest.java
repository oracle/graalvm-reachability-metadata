/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

public class NoBodyOutputStreamTest {
    @Test
    void doHeadInitializesNoBodyOutputStreamAndCountsWrittenBody() throws ServletException, IOException {
        final BodyWritingServlet servlet = new BodyWritingServlet();
        final ContentLengthResponse response = new ContentLengthResponse();

        servlet.invokeDoHead(null, response);

        assertThat(response.contentLength()).isEqualTo(5);
        assertThat(response.outputStreamRequested()).isFalse();
    }

    private static final class BodyWritingServlet extends HttpServlet {
        void invokeDoHead(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            super.doHead(request, response);
        }

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws IOException {
            final ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write('h');
            outputStream.write(new byte[]{'e', 'l', 'l', 'o'}, 0, 4);
        }
    }

    private static final class ContentLengthResponse implements HttpServletResponse {
        private int contentLength = -1;
        private boolean outputStreamRequested;

        int contentLength() {
            return contentLength;
        }

        boolean outputStreamRequested() {
            return outputStreamRequested;
        }

        @Override
        public void setContentLength(final int length) {
            contentLength = length;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            outputStreamRequested = true;
            throw unsupported();
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            throw unsupported();
        }

        @Override
        public PrintWriter getWriter() {
            throw unsupported();
        }

        @Override
        public void setCharacterEncoding(final String charset) {
            throw unsupported();
        }

        @Override
        public void setContentType(final String type) {
            throw unsupported();
        }

        @Override
        public void setBufferSize(final int size) {
            throw unsupported();
        }

        @Override
        public int getBufferSize() {
            throw unsupported();
        }

        @Override
        public void flushBuffer() {
            throw unsupported();
        }

        @Override
        public void resetBuffer() {
            throw unsupported();
        }

        @Override
        public boolean isCommitted() {
            throw unsupported();
        }

        @Override
        public void reset() {
            throw unsupported();
        }

        @Override
        public void setLocale(final Locale locale) {
            throw unsupported();
        }

        @Override
        public Locale getLocale() {
            throw unsupported();
        }

        @Override
        public void addCookie(final Cookie cookie) {
            throw unsupported();
        }

        @Override
        public boolean containsHeader(final String name) {
            throw unsupported();
        }

        @Override
        public String encodeURL(final String url) {
            throw unsupported();
        }

        @Override
        public String encodeRedirectURL(final String url) {
            throw unsupported();
        }

        @Override
        public String encodeUrl(final String url) {
            throw unsupported();
        }

        @Override
        public String encodeRedirectUrl(final String url) {
            throw unsupported();
        }

        @Override
        public void sendError(final int statusCode, final String message) {
            throw unsupported();
        }

        @Override
        public void sendError(final int statusCode) {
            throw unsupported();
        }

        @Override
        public void sendRedirect(final String location) {
            throw unsupported();
        }

        @Override
        public void setDateHeader(final String name, final long date) {
            throw unsupported();
        }

        @Override
        public void addDateHeader(final String name, final long date) {
            throw unsupported();
        }

        @Override
        public void setHeader(final String name, final String value) {
            throw unsupported();
        }

        @Override
        public void addHeader(final String name, final String value) {
            throw unsupported();
        }

        @Override
        public void setIntHeader(final String name, final int value) {
            throw unsupported();
        }

        @Override
        public void addIntHeader(final String name, final int value) {
            throw unsupported();
        }

        @Override
        public void setStatus(final int statusCode) {
            throw unsupported();
        }

        @Override
        public void setStatus(final int statusCode, final String message) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Unexpected response interaction");
        }
    }
}
