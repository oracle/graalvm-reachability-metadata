/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

final class WebResponse implements HttpServletResponse {
    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final List<Cookie> cookies = new ArrayList<>();
    int status = SC_OK;
    int contentLength;
    String contentType;
    private boolean committed;
    private final PrintWriter writer = new PrintWriter(body, false, StandardCharsets.UTF_8);
    private final ServletOutputStream output = new ServletOutputStream() {
        @Override
        public void write(int value) { body.write(value); }
    };

    @Override
    public void addCookie(Cookie cookie) { cookies.add(cookie); }
    @Override
    public boolean containsHeader(String name) { return headers.containsKey(name); }
    @Override
    public void setHeader(String name, String value) { headers.put(name, new ArrayList<>(List.of(value))); }
    @Override
    public void addHeader(String name, String value) { headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value); }
    @Override
    public void setStatus(int code) { status = code; }
    @Override
    public void setStatus(int code, String message) { setStatus(code); }
    @Override
    public String getCharacterEncoding() { return StandardCharsets.UTF_8.name(); }
    @Override
    public String getContentType() { return contentType; }
    @Override
    public ServletOutputStream getOutputStream() { return output; }
    @Override
    public PrintWriter getWriter() { return writer; }
    @Override
    public void setContentLength(int length) { contentLength = length; }
    @Override
    public void setContentType(String type) { contentType = type; }
    @Override
    public void flushBuffer() {
        writer.flush();
        committed = true;
    }
    @Override
    public void resetBuffer() {
        if (committed) {
            throw new IllegalStateException("Response is committed");
        }
        body.reset();
    }
    @Override
    public boolean isCommitted() { return committed; }
    @Override
    public void reset() {
        resetBuffer();
        headers.clear();
        cookies.clear();
        status = SC_OK;
        contentLength = 0;
        contentType = null;
    }
    @Override
    public String encodeURL(String url) { return url; }
    @Override
    public String encodeRedirectURL(String url) { return url; }
    @Override
    public String encodeUrl(String url) { return encodeURL(url); }
    @Override
    public String encodeRedirectUrl(String url) { return encodeRedirectURL(url); }
    @Override
    public void sendError(int code, String message) { throw new UnsupportedOperationException(); }
    @Override
    public void sendError(int code) { throw new UnsupportedOperationException(); }
    @Override
    public void sendRedirect(String location) { throw new UnsupportedOperationException(); }
    @Override
    public void setDateHeader(String name, long date) { throw new UnsupportedOperationException(); }
    @Override
    public void addDateHeader(String name, long date) { throw new UnsupportedOperationException(); }
    @Override
    public void setIntHeader(String name, int value) { setHeader(name, Integer.toString(value)); }
    @Override
    public void addIntHeader(String name, int value) { addHeader(name, Integer.toString(value)); }
    @Override
    public void setCharacterEncoding(String encoding) { throw new UnsupportedOperationException(); }
    @Override
    public void setBufferSize(int size) { throw new UnsupportedOperationException(); }
    @Override
    public int getBufferSize() { throw new UnsupportedOperationException(); }
    @Override
    public void setLocale(Locale locale) { throw new UnsupportedOperationException(); }
    @Override
    public Locale getLocale() { throw new UnsupportedOperationException(); }
}
