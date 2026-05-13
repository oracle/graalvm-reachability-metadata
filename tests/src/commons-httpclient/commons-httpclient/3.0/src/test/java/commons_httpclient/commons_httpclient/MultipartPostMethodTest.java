/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.methods.MultipartPostMethod;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("deprecation")
public class MultipartPostMethodTest {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String BOUNDARY = "----------------314159265358979323846";
    private static final String MULTIPART_POST_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.MultipartPostMethod";

    @Test
    void legacyClassLiteralHelperLoadsClassUsedByPublicClientConfiguration() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MultipartPostMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                MultipartPostMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedMethodClass = (Class<?>) classLookup.invoke(
                MULTIPART_POST_METHOD_CLASS_NAME);
        Class<?> connectionManagerClass = (Class<?>) classLookup.invoke(
                "org.apache.commons.httpclient.SimpleHttpConnectionManager");
        HttpClientParams params = new HttpClientParams();
        params.setConnectionManagerClass(connectionManagerClass);

        HttpClient client = new HttpClient(params);

        assertThat(resolvedMethodClass).isSameAs(MultipartPostMethod.class);
        assertThat(connectionManagerClass).isSameAs(SimpleHttpConnectionManager.class);
        assertThat(client.getHttpConnectionManager())
                .isInstanceOf(SimpleHttpConnectionManager.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (MultipartPostMethodClassLoader classLoader = newMultipartPostMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    MULTIPART_POST_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(MULTIPART_POST_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(MultipartPostMethod.class);
            } else {
                assertThat(methodClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void executeMultipartPostSendsStringAndFileParts(@TempDir Path tempDirectory) throws Exception {
        Path uploadFile = tempDirectory.resolve("upload.txt");
        Files.writeString(uploadFile, "file-body", StandardCharsets.ISO_8859_1);
        InetAddress loopbackAddress = InetAddress.getByName("127.0.0.1");

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<ServerObservation> observation = new FutureTask<>(
                    () -> serveSingleMultipartResponse(serverSocket));
            Thread serverThread = new Thread(observation,
                    "commons-httpclient-multipart-post-server");
            serverThread.start();

            MultipartPostMethod method = new MultipartPostMethod("http://"
                    + loopbackAddress.getHostAddress() + ":" + serverSocket.getLocalPort()
                    + "/upload");
            method.addParameter("description", "text value");
            method.addParameter("payload", "message.txt", uploadFile.toFile());
            Part[] parts = method.getParts();
            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT_MILLIS);
            client.getHttpConnectionManager().getParams().setSoTimeout(TIMEOUT_MILLIS);

            try {
                int statusCode = client.executeMethod(method);
                ServerObservation request = observation.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

                assertThat(statusCode).isEqualTo(HttpStatus.SC_OK);
                assertThat(method.getResponseBodyAsString()).isEqualTo("multipart-ok");
                assertThat(method.getName()).isEqualTo("POST");
                assertThat(parts).hasSize(2);
                assertThat(parts[0].getName()).isEqualTo("description");
                assertThat(parts[1].getName()).isEqualTo("payload");
                assertThat(request.requestLine).isEqualTo("POST /upload HTTP/1.1");
                assertThat(request.headers.toLowerCase(Locale.ROOT))
                        .contains("content-type: multipart/form-data; boundary=" + BOUNDARY)
                        .contains("content-length:");
                assertThat(request.body)
                        .contains("Content-Disposition: form-data; name=\"description\"")
                        .contains("text value")
                        .contains("Content-Disposition: form-data; name=\"payload\"; "
                                + "filename=\"message.txt\"")
                        .contains("Content-Type: application/octet-stream; charset=ISO-8859-1")
                        .contains("Content-Transfer-Encoding: binary")
                        .contains("file-body")
                        .contains("--" + BOUNDARY + "--");
            } finally {
                method.releaseConnection();
                serverSocket.close();
                serverThread.join(TIMEOUT_MILLIS);
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static MultipartPostMethodClassLoader newMultipartPostMethodClassLoader() {
        URL location = HttpClient.class.getProtectionDomain().getCodeSource().getLocation();
        return new MultipartPostMethodClassLoader(new URL[] {location},
                MultipartPostMethodTest.class.getClassLoader());
    }

    private static ServerObservation serveSingleMultipartResponse(
            ServerSocket serverSocket) throws Exception {
        serverSocket.setSoTimeout(TIMEOUT_MILLIS);
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            InputStream input = socket.getInputStream();
            String headers = readHeaders(input);
            int contentLength = parseContentLength(headers);
            byte[] body = input.readNBytes(contentLength);

            byte[] responseBody = "multipart-ok".getBytes(StandardCharsets.ISO_8859_1);
            String responseHeaders = "HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + responseBody.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(responseHeaders.getBytes(StandardCharsets.ISO_8859_1));
            output.write(responseBody);
            output.flush();

            String[] headerLines = headers.split("\r\n");
            return new ServerObservation(
                    headerLines[0],
                    headers,
                    new String(body, StandardCharsets.ISO_8859_1));
        }
    }

    private static String readHeaders(InputStream input) throws Exception {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        byte[] delimiter = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int matched = 0;
        while (headers.size() < 8192) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            headers.write(value);
            if (value == delimiter[matched]) {
                matched++;
                if (matched == delimiter.length) {
                    return headers.toString(StandardCharsets.ISO_8859_1);
                }
            } else {
                matched = value == delimiter[0] ? 1 : 0;
            }
        }
        throw new IllegalStateException("HTTP headers were not terminated");
    }

    private static int parseContentLength(String headers) {
        String[] headerLines = headers.split("\r\n");
        for (String headerLine : headerLines) {
            String normalizedHeader = headerLine.toLowerCase(Locale.ROOT);
            if (normalizedHeader.startsWith("content-length:")) {
                return Integer.parseInt(headerLine.substring(headerLine.indexOf(':') + 1).trim());
            }
        }
        throw new IllegalStateException("Content-Length header was not present");
    }

    private static final class MultipartPostMethodClassLoader extends URLClassLoader {
        private MultipartPostMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (MULTIPART_POST_METHOD_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }

    private static final class ServerObservation {
        private final String requestLine;
        private final String headers;
        private final String body;

        private ServerObservation(String requestLine, String headers, String body) {
            this.requestLine = requestLine;
            this.headers = headers;
            this.body = body;
        }
    }
}
