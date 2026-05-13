/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class OptionsMethodTest {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String OPTIONS_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.OptionsMethod";

    @Test
    void compilerGeneratedClassLookupResolvesOptionsMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                OptionsMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                OptionsMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String optionsMethodClassName = new StringBuilder("org.apache.commons.httpclient.methods")
                .append(".OptionsMethod")
                .toString();

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(optionsMethodClassName);

        assertThat(resolvedClass).isEqualTo(OptionsMethod.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (OptionsMethodClassLoader classLoader = newOptionsMethodClassLoader()) {
            Class<?> methodClass = Class.forName(
                    OPTIONS_METHOD_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(methodClass.getName()).isEqualTo(OPTIONS_METHOD_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(methodClass).isSameAs(OptionsMethod.class);
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
    @SuppressWarnings("deprecation")
    void constructorInitializesOptionsMethodDefaults() throws Exception {
        OptionsMethod method = new OptionsMethod("/capabilities?verbose=true");

        assertThat(method.getName()).isEqualTo("OPTIONS");
        assertThat(method.needContentLength()).isFalse();
        assertThat(method.getPath()).isEqualTo("/capabilities");
        assertThat(method.getQueryString()).isEqualTo("verbose=true");
    }

    @Test
    void executeParsesAllowHeaderIntoAllowedMethods() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            FutureTask<String> serverResponse = new FutureTask<>(
                    () -> writeOptionsResponseToAcceptedSocket(serverSocket));
            Thread serverThread = new Thread(serverResponse, "commons-httpclient-options-server");
            serverThread.start();

            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT_MILLIS);
            client.getHttpConnectionManager().getParams().setSoTimeout(TIMEOUT_MILLIS);
            OptionsMethod method = new OptionsMethod("http://" + loopbackAddress.getHostAddress()
                    + ":" + serverSocket.getLocalPort() + "/capabilities");

            try {
                int statusCode = client.executeMethod(method);

                assertThat(statusCode).isEqualTo(HttpStatus.SC_OK);
                assertThat(method.isAllowed("GET")).isTrue();
                assertThat(method.isAllowed("POST")).isTrue();
                assertThat(method.isAllowed("TRACE")).isFalse();
                assertThat(toList(method.getAllowedMethods()))
                        .containsExactly("GET", "HEAD", "OPTIONS", "POST");
                assertThat(serverResponse.get(5, TimeUnit.SECONDS))
                        .startsWith("OPTIONS /capabilities HTTP/1.1");
            } finally {
                method.releaseConnection();
                serverSocket.close();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static String writeOptionsResponseToAcceptedSocket(ServerSocket serverSocket)
            throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            String requestHeaders = readRequestHeaders(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\n"
                    + "Allow: GET, HEAD, OPTIONS, POST\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return requestHeaders;
        }
    }

    private static String readRequestHeaders(InputStream input) throws Exception {
        StringBuilder headers = new StringBuilder();
        int previous = -1;
        int current = -1;
        int matched = 0;
        while (matched < 4) {
            previous = current;
            current = input.read();
            if (current == -1) {
                throw new AssertionError("HTTP request ended before headers were complete");
            }
            headers.append((char) current);
            if ((matched == 0 && current == '\r')
                    || (matched == 1 && current == '\n')
                    || (matched == 2 && previous == '\n' && current == '\r')
                    || (matched == 3 && current == '\n')) {
                matched++;
            } else {
                matched = current == '\r' ? 1 : 0;
            }
        }
        return headers.toString();
    }

    private static List<String> toList(Enumeration<?> methods) {
        List<String> values = new ArrayList<>();
        while (methods.hasMoreElements()) {
            values.add((String) methods.nextElement());
        }
        return values;
    }

    private static OptionsMethodClassLoader newOptionsMethodClassLoader() {
        URL location = OptionsMethod.class.getProtectionDomain().getCodeSource().getLocation();
        return new OptionsMethodClassLoader(
                new URL[] {location},
                OptionsMethodTest.class.getClassLoader());
    }

    private static final class OptionsMethodClassLoader extends URLClassLoader {
        private OptionsMethodClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (OPTIONS_METHOD_CLASS_NAME.equals(name)) {
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
