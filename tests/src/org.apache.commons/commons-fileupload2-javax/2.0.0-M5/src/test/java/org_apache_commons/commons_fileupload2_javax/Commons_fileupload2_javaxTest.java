/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_fileupload2_javax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadContentTypeException;
import org.apache.commons.fileupload2.javax.JavaxFileCleaner;
import org.apache.commons.fileupload2.javax.JavaxServletDiskFileUpload;
import org.apache.commons.fileupload2.javax.JavaxServletFileUpload;
import org.apache.commons.fileupload2.javax.JavaxServletRequestContext;
import org.apache.commons.io.FileCleaningTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Commons_fileupload2_javaxTest {
    private static final String CRLF = "\r\n";

    @TempDir
    Path tempDir;

    @Test
    void parsesServletMultipartRequestIntoDiskItems() throws Exception {
        String boundary = "javax-boundary";
        String descriptionText = "R\u00e9sum\u00e9 caf\u00e9";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"description\"",
                        "Content-Type: text/plain; charset=UTF-8",
                        "",
                        descriptionText),
                part("Content-Disposition: form-data; name=\"upload\"; filename=\"notes.txt\"",
                        "Content-Type: text/plain",
                        "",
                        "alpha\nbeta"));
        MemoryHttpServletRequest request = multipartRequest("POST", boundary, body, StandardCharsets.UTF_8);
        JavaxServletDiskFileUpload upload = newDiskUpload(8);

        List<DiskFileItem> items = upload.parseRequest(request);

        assertThat(JavaxServletFileUpload.isMultipartContent(request)).isTrue();
        assertThat(items).hasSize(2);
        DiskFileItem description = items.get(0);
        assertThat(description.getFieldName()).isEqualTo("description");
        assertThat(description.isFormField()).isTrue();
        assertThat(description.getName()).isNull();
        assertThat(description.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(description.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(description.getString()).isEqualTo(descriptionText);

        DiskFileItem file = items.get(1);
        assertThat(file.getFieldName()).isEqualTo("upload");
        assertThat(file.isFormField()).isFalse();
        assertThat(file.getName()).isEqualTo("notes.txt");
        assertThat(file.getContentType()).isEqualTo("text/plain");
        assertThat(file.getString(StandardCharsets.UTF_8)).isEqualTo("alpha\nbeta");
        assertThat(file.isInMemory()).isFalse();
        assertThat(file.getPath()).isNotNull();
    }

    @Test
    void streamsServletRequestPartsWithoutMaterializingFileItems() throws Exception {
        String boundary = "streaming";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"token\"", "", "abc123"),
                part("Content-Disposition: form-data; name=\"payload\"; filename=\"data.json\"",
                        "Content-Type: application/json",
                        "",
                        "{\"ok\":true}"));
        JavaxServletDiskFileUpload upload = newDiskUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        MemoryHttpServletRequest request = multipartRequest("POST", boundary, body, StandardCharsets.UTF_8);
        JavaxServletRequestContext context = new JavaxServletRequestContext(request);

        assertThat(context.getContentType()).isEqualTo(request.getContentType());
        assertThat(context.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(context.getContentLength()).isEqualTo((long) body.length);
        assertThat(readUtf8(context.getInputStream())).contains("name=\"token\"");

        FileItemInputIterator iterator = upload.getItemIterator(request);

        assertThat(iterator.hasNext()).isTrue();
        FileItemInput token = iterator.next();
        assertThat(token.isFormField()).isTrue();
        assertThat(token.getFieldName()).isEqualTo("token");
        assertThat(token.getName()).isNull();
        assertThat(readUtf8(token.getInputStream())).isEqualTo("abc123");

        assertThat(iterator.hasNext()).isTrue();
        FileItemInput payload = iterator.next();
        assertThat(payload.isFormField()).isFalse();
        assertThat(payload.getFieldName()).isEqualTo("payload");
        assertThat(payload.getName()).isEqualTo("data.json");
        assertThat(payload.getContentType()).isEqualTo("application/json");
        assertThat(readUtf8(payload.getInputStream())).isEqualTo("{\"ok\":true}");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void groupsRepeatedServletFormFieldsInParameterMap() throws Exception {
        String boundary = "parameter-map";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"tag\"", "", "red"),
                part("Content-Disposition: form-data; name=\"tag\"", "", "blue"),
                part("Content-Disposition: form-data; name=\"single\"", "", "value"));
        JavaxServletDiskFileUpload upload = newDiskUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        MemoryHttpServletRequest request = multipartRequest("POST", boundary, body, StandardCharsets.UTF_8);

        Map<String, List<DiskFileItem>> parameterMap = upload.parseParameterMap(request);

        assertThat(parameterMap).containsOnlyKeys("tag", "single");
        List<DiskFileItem> tags = parameterMap.get("tag");
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).getString()).isEqualTo("red");
        assertThat(tags.get(1).getString()).isEqualTo("blue");
        List<DiskFileItem> single = parameterMap.get("single");
        assertThat(single).hasSize(1);
        assertThat(single.get(0).getString()).isEqualTo("value");
    }

    @Test
    void decodesMultipartHeadersWithConfiguredHeaderCharset() throws Exception {
        String boundary = "latin-headers";
        String fileName = "r\u00e9sum\u00e9.txt";
        byte[] body = multipartEncoded(StandardCharsets.ISO_8859_1, boundary,
                part("Content-Disposition: form-data; name=\"document\"; filename=\"" + fileName + "\"",
                        "Content-Type: text/plain",
                        "",
                        "plain text"));
        MemoryHttpServletRequest request = multipartRequest("POST", boundary, body, StandardCharsets.UTF_8);
        JavaxServletDiskFileUpload upload = newDiskUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        upload.setHeaderCharset(StandardCharsets.ISO_8859_1);

        List<DiskFileItem> items = upload.parseRequest(request);

        assertThat(upload.getHeaderCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(items).hasSize(1);
        DiskFileItem document = items.get(0);
        assertThat(document.getFieldName()).isEqualTo("document");
        assertThat(document.isFormField()).isFalse();
        assertThat(document.getName()).isEqualTo(fileName);
        assertThat(document.getContentType()).isEqualTo("text/plain");
        assertThat(document.getString(StandardCharsets.UTF_8)).isEqualTo("plain text");
    }

    @Test
    void rejectsRequestsThatServletUploadMustNotParse() throws Exception {
        String boundary = "reject";
        byte[] body = multipart(boundary, part("Content-Disposition: form-data; name=\"field\"", "", "value"));
        JavaxServletDiskFileUpload upload = newDiskUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);

        MemoryHttpServletRequest getRequest = multipartRequest("GET", boundary, body, StandardCharsets.UTF_8);
        assertThat(JavaxServletFileUpload.isMultipartContent(getRequest)).isFalse();
        MemoryHttpServletRequest plainTextRequest = new MemoryHttpServletRequest("POST", "text/plain", body,
                StandardCharsets.UTF_8);
        assertThat(JavaxServletFileUpload.isMultipartContent(plainTextRequest)).isFalse();
        assertThatExceptionOfType(FileUploadContentTypeException.class)
                .isThrownBy(() -> upload.getItemIterator(plainTextRequest))
                .satisfies(exception -> assertThat(exception.getContentType()).isEqualTo("text/plain"));

        JavaxServletDiskFileUpload sizeLimitedUpload = newDiskUpload(DiskFileItemFactory.DEFAULT_THRESHOLD);
        sizeLimitedUpload.setFileSizeMax(3);
        byte[] largeBody = multipart(boundary,
                part("Content-Disposition: form-data; name=\"payload\"; filename=\"payload.txt\"",
                        "Content-Length: 4",
                        "",
                        "data"));
        MemoryHttpServletRequest largeRequest = multipartRequest("POST", boundary, largeBody, StandardCharsets.UTF_8);
        assertThatExceptionOfType(FileUploadByteCountLimitException.class)
                .isThrownBy(() -> sizeLimitedUpload.parseRequest(largeRequest))
                .satisfies(exception -> {
                    assertThat(exception.getFieldName()).isEqualTo("payload");
                    assertThat(exception.getFileName()).isEqualTo("payload.txt");
                    assertThat(exception.getPermitted()).isEqualTo(3);
                    assertThat(exception.getActualSize()).isEqualTo(4);
                });
    }

    @Test
    void writesParsedServletUploadToApplicationFile() throws Exception {
        String boundary = "write-upload";
        String content = "line one\nline two\nline three";
        byte[] body = multipart(boundary,
                part("Content-Disposition: form-data; name=\"upload\"; filename=\"report.txt\"",
                        "Content-Type: text/plain; charset=UTF-8",
                        "",
                        content));
        JavaxServletDiskFileUpload upload = newDiskUpload(8);
        MemoryHttpServletRequest request = multipartRequest("POST", boundary, body, StandardCharsets.UTF_8);

        DiskFileItem item = upload.parseRequest(request).get(0);
        Path destination = tempDir.resolve("stored-report.txt");

        DiskFileItem writtenItem = item.write(destination);

        assertThat(writtenItem).isSameAs(item);
        assertThat(item.getSize()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    @Test
    void managesFileCleaningTrackerThroughServletContextLifecycle() {
        MemoryServletContext servletContext = new MemoryServletContext();
        JavaxFileCleaner listener = new JavaxFileCleaner();

        listener.contextInitialized(new ServletContextEvent(servletContext));
        FileCleaningTracker tracker = JavaxFileCleaner.getFileCleaningTracker(servletContext);

        assertThat(tracker).isNotNull();
        assertThat(servletContext.getAttribute(JavaxFileCleaner.FILE_CLEANING_TRACKER_ATTRIBUTE)).isSameAs(tracker);

        FileCleaningTracker replacement = new FileCleaningTracker();
        JavaxFileCleaner.setFileCleaningTracker(servletContext, replacement);
        assertThat(JavaxFileCleaner.getFileCleaningTracker(servletContext)).isSameAs(replacement);

        listener.contextDestroyed(new ServletContextEvent(servletContext));
        assertThat(replacement.getTrackCount()).isZero();
    }

    private JavaxServletDiskFileUpload newDiskUpload(final int threshold) {
        DiskFileItemFactory factory = DiskFileItemFactory.builder()
                .setPath(tempDir)
                .setBufferSize(threshold)
                .setCharset(StandardCharsets.ISO_8859_1)
                .get();
        JavaxServletDiskFileUpload upload = new JavaxServletDiskFileUpload(factory);
        assertThat(upload.getFileItemFactory()).isSameAs(factory);
        return upload;
    }

    private static MemoryHttpServletRequest multipartRequest(final String method, final String boundary,
            final byte[] body, final Charset charset) {
        return new MemoryHttpServletRequest(method, "multipart/form-data; boundary=" + boundary, body, charset);
    }

    private static byte[] multipart(final String boundary, final String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append("--").append(boundary).append(CRLF).append(part).append(CRLF);
        }
        builder.append("--").append(boundary).append("--").append(CRLF);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] multipartEncoded(final Charset charset, final String boundary, final String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append("--").append(boundary).append(CRLF).append(part).append(CRLF);
        }
        builder.append("--").append(boundary).append("--").append(CRLF);
        return builder.toString().getBytes(charset);
    }

    private static String part(final String... lines) {
        return String.join(CRLF, lines);
    }

    private static String readUtf8(final InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not needed by these servlet integration tests");
    }

    private static final class MemoryServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        private MemoryServletInputStream(final byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw unsupported();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class MemoryHttpServletRequest implements HttpServletRequest {
        private final String method;
        private final String contentType;
        private final byte[] body;
        private final Charset charset;
        private final Map<String, Object> attributes = new HashMap<>();

        private MemoryHttpServletRequest(final String method, final String contentType, final byte[] body,
                final Charset charset) {
            this.method = method;
            this.contentType = contentType;
            this.body = body.clone();
            this.charset = charset;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getCharacterEncoding() {
            return charset.name();
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new MemoryServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }

        @Override
        public Object getAttribute(final String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(final String name) {
            attributes.remove(name);
        }

        @Override
        public void setCharacterEncoding(final String env) {
            throw unsupported();
        }

        @Override
        public String getParameter(final String name) {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(final String name) {
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 80;
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHost() {
            return "localhost";
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(Locale.ROOT));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String path) {
            return null;
        }

        @Override
        public String getRealPath(final String path) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 80;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() {
            throw unsupported();
        }

        @Override
        public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse) {
            throw unsupported();
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST;
        }

        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public Cookie[] getCookies() {
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(final String name) {
            return -1;
        }

        @Override
        public String getHeader(final String name) {
            if ("content-type".equalsIgnoreCase(name)) {
                return contentType;
            }
            if ("content-length".equalsIgnoreCase(name)) {
                return Integer.toString(body.length);
            }
            return null;
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            String header = getHeader(name);
            return header == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(header));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(List.of("content-type", "content-length"));
        }

        @Override
        public int getIntHeader(final String name) {
            String header = getHeader(name);
            return header == null ? -1 : Integer.parseInt(header);
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(final String role) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return "/upload";
        }

        @Override
        public StringBuffer getRequestURL() {
            return javax.servlet.http.HttpUtils.getRequestURL(this);
        }

        @Override
        public String getServletPath() {
            return "/upload";
        }

        @Override
        public HttpSession getSession(final boolean create) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public String changeSessionId() {
            throw unsupported();
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }

        @Override
        public boolean authenticate(final HttpServletResponse response) {
            throw unsupported();
        }

        @Override
        public void login(final String username, final String password) {
            throw unsupported();
        }

        @Override
        public void logout() {
            throw unsupported();
        }

        @Override
        public Collection<Part> getParts() {
            return Collections.emptyList();
        }

        @Override
        public Part getPart(final String name) {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(final Class<T> handlerClass) {
            throw unsupported();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class MemoryServletContext implements ServletContext {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Object getAttribute(final String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public void setAttribute(final String name, final Object object) {
            attributes.put(name, object);
        }

        @Override
        public void removeAttribute(final String name) {
            attributes.remove(name);
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public ServletContext getContext(final String uripath) {
            return null;
        }

        @Override
        public int getMajorVersion() {
            return 4;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public int getEffectiveMajorVersion() {
            return 4;
        }

        @Override
        public int getEffectiveMinorVersion() {
            return 0;
        }

        @Override
        public String getMimeType(final String file) {
            return null;
        }

        @Override
        public Set<String> getResourcePaths(final String path) {
            return Collections.emptySet();
        }

        @Override
        public URL getResource(final String path) throws MalformedURLException {
            return null;
        }

        @Override
        public InputStream getResourceAsStream(final String path) {
            return null;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String path) {
            return null;
        }

        @Override
        public RequestDispatcher getNamedDispatcher(final String name) {
            return null;
        }

        @Override
        public Servlet getServlet(final String name) {
            return null;
        }

        @Override
        public Enumeration<Servlet> getServlets() {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getServletNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void log(final String msg) {
        }

        @Override
        public void log(final Exception exception, final String msg) {
        }

        @Override
        public void log(final String message, final Throwable throwable) {
        }

        @Override
        public String getRealPath(final String path) {
            return null;
        }

        @Override
        public String getServerInfo() {
            return "memory";
        }

        @Override
        public String getInitParameter(final String name) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public boolean setInitParameter(final String name, final String value) {
            return false;
        }

        @Override
        public String getServletContextName() {
            return "memory";
        }

        @Override
        public ServletRegistration.Dynamic addServlet(final String servletName, final String className) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addServlet(final String servletName,
                final Class<? extends Servlet> servletClass) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addJspFile(final String servletName, final String jspFile) {
            throw unsupported();
        }

        @Override
        public <T extends Servlet> T createServlet(final Class<T> clazz) {
            throw unsupported();
        }

        @Override
        public ServletRegistration getServletRegistration(final String servletName) {
            return null;
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
            throw unsupported();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
            throw unsupported();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(final String filterName,
                final Class<? extends Filter> filterClass) {
            throw unsupported();
        }

        @Override
        public <T extends Filter> T createFilter(final Class<T> clazz) {
            throw unsupported();
        }

        @Override
        public FilterRegistration getFilterRegistration(final String filterName) {
            return null;
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            throw unsupported();
        }

        @Override
        public void setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {
            throw unsupported();
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public void addListener(final String className) {
            throw unsupported();
        }

        @Override
        public <T extends EventListener> void addListener(final T listener) {
            throw unsupported();
        }

        @Override
        public void addListener(final Class<? extends EventListener> listenerClass) {
            throw unsupported();
        }

        @Override
        public <T extends EventListener> T createListener(final Class<T> clazz) {
            throw unsupported();
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public void declareRoles(final String... roleNames) {
        }

        @Override
        public String getVirtualServerName() {
            return "memory";
        }

        @Override
        public int getSessionTimeout() {
            return 0;
        }

        @Override
        public void setSessionTimeout(final int sessionTimeout) {
        }

        @Override
        public String getRequestCharacterEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public void setRequestCharacterEncoding(final String encoding) {
        }

        @Override
        public String getResponseCharacterEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public void setResponseCharacterEncoding(final String encoding) {
        }
    }
}
