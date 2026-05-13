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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.util.HttpURLConnection;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpURLConnectionTest {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String HTTP_URL_CONNECTION_CLASS_NAME =
            "org.apache.commons.httpclient.util.HttpURLConnection";

    @Test
    void legacyClassLiteralHelperResolvesHttpUrlConnectionType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HttpURLConnection.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HttpURLConnection.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String className = new StringBuilder("org.apache.commons.httpclient.util")
                .append(".HttpURLConnection")
                .toString();

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(className);

        assertThat(resolvedClass).isSameAs(HttpURLConnection.class);
        assertThat(className).isEqualTo(HTTP_URL_CONNECTION_CLASS_NAME);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpURLConnectionClassLoader classLoader = newHttpURLConnectionClassLoader()) {
            Class<?> connectionClass = Class.forName(
                    HTTP_URL_CONNECTION_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(connectionClass.getName()).isEqualTo(HTTP_URL_CONNECTION_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(connectionClass).isSameAs(HttpURLConnection.class);
            } else {
                assertThat(connectionClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void wrapsExecutedGetMethodAsJdkHttpUrlConnection() throws Exception {
        byte[] responseBody = "wrapped-body".getBytes(StandardCharsets.ISO_8859_1);
        InetAddress loopbackAddress = InetAddress.getByName("127.0.0.1");

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<String> request = new FutureTask<>(
                    () -> serveSingleResponse(serverSocket, responseBody));
            Thread serverThread = new Thread(request, "commons-httpclient-url-connection-server");
            serverThread.start();

            URL url = new URL("http://" + loopbackAddress.getHostAddress() + ":"
                    + serverSocket.getLocalPort() + "/resource?name=value");
            GetMethod method = new GetMethod(url.toString());
            SimpleHttpConnectionManager connectionManager = new SimpleHttpConnectionManager();
            connectionManager.getParams().setConnectionTimeout(TIMEOUT_MILLIS);
            connectionManager.getParams().setSoTimeout(TIMEOUT_MILLIS);
            HttpClient client = new HttpClient(connectionManager);

            try {
                int statusCode = client.executeMethod(method);
                HttpURLConnection connection = new HttpURLConnection(method, url);

                assertThat(statusCode).isEqualTo(202);
                assertThat(connection.getURL()).isEqualTo(url);
                assertThat(connection.getRequestMethod()).isEqualTo("GET");
                assertThat(connection.getResponseCode()).isEqualTo(202);
                assertThat(connection.getResponseMessage()).isEqualTo("Accepted");
                assertThat(connection.getHeaderField("X-Repeat")).isEqualTo("second");
                assertThat(connection.getHeaderField("Content-Type")).isEqualTo("text/plain");
                assertThat(connection.getHeaderFieldKey(0)).isNull();
                assertThat(connection.getHeaderField(0)).isEqualTo("HTTP/1.1 202 Accepted");
                assertThat(readFully(connection.getInputStream())).isEqualTo("wrapped-body");
                assertThat(request.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        .startsWith("GET /resource?name=value HTTP/1.1");
            } finally {
                method.releaseConnection();
                connectionManager.closeIdleConnections(0);
                serverSocket.close();
                serverThread.join(TIMEOUT_MILLIS);
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static HttpURLConnectionClassLoader newHttpURLConnectionClassLoader() {
        URL location = HttpURLConnection.class.getProtectionDomain().getCodeSource().getLocation();
        return new HttpURLConnectionClassLoader(
                new URL[] {location},
                HttpURLConnectionTest.class.getClassLoader());
    }

    private static String serveSingleResponse(
            ServerSocket serverSocket,
            byte[] responseBody) throws Exception {
        serverSocket.setSoTimeout(TIMEOUT_MILLIS);
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            String requestHeaders = readHeaders(socket.getInputStream());
            String responseHeaders = "HTTP/1.1 202 Accepted\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "X-Repeat: first\r\n"
                    + "X-Repeat: second\r\n"
                    + "Content-Length: " + responseBody.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(responseHeaders.getBytes(StandardCharsets.ISO_8859_1));
            output.write(responseBody);
            output.flush();
            return requestHeaders;
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

    private static String readFully(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    private static final class HttpURLConnectionClassLoader extends URLClassLoader {
        private HttpURLConnectionClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_URL_CONNECTION_CLASS_NAME.equals(name)) {
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
}
