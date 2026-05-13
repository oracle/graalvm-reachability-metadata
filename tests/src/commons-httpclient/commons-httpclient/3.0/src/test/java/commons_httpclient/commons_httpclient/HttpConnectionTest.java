/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpConnection;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpConnectionTest {
    private static final String HTTP_CONNECTION_CLASS_NAME = "org.apache.commons.httpclient.HttpConnection";

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpConnectionClassLoader classLoader = newHttpConnectionClassLoader()) {
            Class<?> connectionClass = Class.forName(HTTP_CONNECTION_CLASS_NAME, true, classLoader);

            assertThat(connectionClass.getName()).isEqualTo(HTTP_CONNECTION_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(connectionClass).isSameAs(HttpConnection.class);
            } else {
                assertThat(connectionClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void shutdownOutputClosesClientWriteSide() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<ServerObservation> serverRead = new FutureTask<>(
                    () -> readUntilEndOfStream(serverSocket));
            Thread serverThread = new Thread(serverRead, "commons-httpclient-shutdown-output-server");
            serverThread.start();

            HttpConnection connection = new HttpConnection(loopbackAddress.getHostAddress(),
                    serverSocket.getLocalPort());
            connection.getParams().setSoTimeout(5000);

            try {
                connection.open();
                connection.write(new byte[] {'p'});
                connection.flushRequestOutputStream();

                connection.shutdownOutput();

                ServerObservation observation = serverRead.get(5, TimeUnit.SECONDS);
                assertThat(observation.firstByte).isEqualTo('p');
                assertThat(observation.endOfStreamReached).isTrue();
            } finally {
                connection.close();
                serverSocket.close();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static HttpConnectionClassLoader newHttpConnectionClassLoader() {
        URL location = HttpConnection.class.getProtectionDomain().getCodeSource().getLocation();
        return new HttpConnectionClassLoader(new URL[] {location},
                HttpConnectionTest.class.getClassLoader());
    }

    private static ServerObservation readUntilEndOfStream(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            int firstByte = input.read();
            int currentByte = firstByte;
            while (currentByte != -1) {
                currentByte = input.read();
            }
            return new ServerObservation(firstByte, true);
        }
    }

    private static final class HttpConnectionClassLoader extends URLClassLoader {
        private HttpConnectionClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_CONNECTION_CLASS_NAME.equals(name)) {
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
        private final int firstByte;
        private final boolean endOfStreamReached;

        private ServerObservation(int firstByte, boolean endOfStreamReached) {
            this.firstByte = firstByte;
            this.endOfStreamReached = endOfStreamReached;
        }
    }
}
