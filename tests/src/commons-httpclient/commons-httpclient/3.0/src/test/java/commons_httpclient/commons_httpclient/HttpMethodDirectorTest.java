/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpMethodDirectorTest {
    private static final String HTTP_METHOD_DIRECTOR_CLASS_NAME =
            "org.apache.commons.httpclient.HttpMethodDirector";
    private static final int TIMEOUT_MILLIS = 5000;

    @Test
    void compilerGeneratedClassLookupResolvesDirectorType() throws Throwable {
        Class<?> directorClass = Class.forName(
                HTTP_METHOD_DIRECTOR_CLASS_NAME,
                false,
                HttpClient.class.getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                directorClass,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                directorClass,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(HTTP_METHOD_DIRECTOR_CLASS_NAME);

        assertThat(resolvedClass.getName()).isEqualTo(HTTP_METHOD_DIRECTOR_CLASS_NAME);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpMethodDirectorClassLoader classLoader = newHttpMethodDirectorClassLoader()) {
            Class<?> directorClass = Class.forName(
                    HTTP_METHOD_DIRECTOR_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(directorClass.getName()).isEqualTo(HTTP_METHOD_DIRECTOR_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void executeMethodUsesDirectorForSuccessfulGetRequest() throws Exception {
        InetAddress loopbackAddress = InetAddress.getByName("127.0.0.1");

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<String> requestLine = new FutureTask<>(
                    () -> serveSingleOkResponse(serverSocket));
            Thread serverThread = new Thread(requestLine,
                    "commons-httpclient-director-server");
            serverThread.start();

            GetMethod method = new GetMethod("http://" + loopbackAddress.getHostAddress()
                    + ":" + serverSocket.getLocalPort() + "/director");
            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT_MILLIS);
            client.getHttpConnectionManager().getParams().setSoTimeout(TIMEOUT_MILLIS);

            try {
                int statusCode = client.executeMethod(method);

                assertThat(statusCode).isEqualTo(HttpStatus.SC_OK);
                assertThat(method.getResponseBodyAsString()).isEqualTo("director-ok");
                assertThat(requestLine.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        .isEqualTo("GET /director HTTP/1.1");
            } finally {
                method.releaseConnection();
                serverSocket.close();
                serverThread.join(TIMEOUT_MILLIS);
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static HttpMethodDirectorClassLoader newHttpMethodDirectorClassLoader() {
        URL location = HttpClient.class.getProtectionDomain().getCodeSource().getLocation();
        return new HttpMethodDirectorClassLoader(new URL[] {location},
                HttpMethodDirectorTest.class.getClassLoader());
    }

    private static String serveSingleOkResponse(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            String headerLine = reader.readLine();
            while (headerLine != null && !headerLine.isEmpty()) {
                headerLine = reader.readLine();
            }

            byte[] body = "director-ok".getBytes(StandardCharsets.ISO_8859_1);
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(response.getBytes(StandardCharsets.ISO_8859_1));
            output.write(body);
            output.flush();
            return requestLine;
        }
    }

    private static final class HttpMethodDirectorClassLoader extends URLClassLoader {
        private HttpMethodDirectorClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_METHOD_DIRECTOR_CLASS_NAME.equals(name)) {
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
