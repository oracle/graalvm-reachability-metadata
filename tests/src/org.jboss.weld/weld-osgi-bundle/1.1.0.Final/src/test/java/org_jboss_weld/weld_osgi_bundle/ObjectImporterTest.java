/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.tools.rmi.ObjectImporter;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.RemoteRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ObjectImporterTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    @Test
    void staticInitializerUsesCompilerGeneratedClassResolver() throws Exception {
        IsolatedObjectImporterClassLoader classLoader = new IsolatedObjectImporterClassLoader(
                ObjectImporterTest.class.getClassLoader());
        try {
            Class<?> reloadedClass = Class.forName(ObjectImporter.class.getName(), true, classLoader);

            assertThat(reloadedClass.getName()).isEqualTo(ObjectImporter.class.getName());
            assertThat(reloadedClass.getClassLoader()).isSameAs(classLoader);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void lookupObjectCreatesProxyFromRemoteClassName() throws Exception {
        try (RmiServer server = RmiServer.start(socket -> {
            InputStream inputStream = socket.getInputStream();
            assertThat(readHeaders(inputStream)).isEqualTo("POST /lookup HTTP/1.0");

            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            assertThat(objectInputStream.readUTF()).isEqualTo("greeter");

            OutputStream outputStream = socket.getOutputStream();
            writeHttpHeader(outputStream);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeInt(42);
            objectOutputStream.writeUTF(RemoteGreeting.class.getName());
            objectOutputStream.flush();
        })) {
            ObjectImporter importer = new ObjectImporter(
                    InetAddress.getLoopbackAddress().getHostAddress(), server.port());

            Object object = importer.lookupObject("greeter");

            assertThat(object).isInstanceOf(RemoteGreeting.class);
            RemoteGreeting greeting = (RemoteGreeting) object;
            assertThat(greeting.importer).isSameAs(importer);
            assertThat(greeting._getObjectId()).isEqualTo(42);
            server.await();
        }
    }

    @Test
    void callWritesProxyAndRegularParametersThenReadsSuccessfulResult() throws Exception {
        try (RmiServer server = RmiServer.start(socket -> {
            InputStream inputStream = socket.getInputStream();
            assertThat(readHeaders(inputStream)).isEqualTo("POST /rmi HTTP/1.0");

            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            assertThat(objectInputStream.readInt()).isEqualTo(11);
            assertThat(objectInputStream.readInt()).isEqualTo(12);
            assertThat(objectInputStream.readInt()).isEqualTo(2);

            Object firstArgument = objectInputStream.readObject();
            Object secondArgument = objectInputStream.readObject();

            assertThat(firstArgument).isInstanceOf(RemoteRef.class);
            RemoteRef remoteRef = (RemoteRef) firstArgument;
            assertThat(remoteRef.oid).isEqualTo(77);
            assertThat(remoteRef.classname).isNull();
            assertThat(secondArgument).isEqualTo("payload");

            OutputStream outputStream = socket.getOutputStream();
            writeHttpHeader(outputStream);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeBoolean(true);
            objectOutputStream.writeObject("remote result");
            objectOutputStream.flush();
        })) {
            ObjectImporter importer = new ObjectImporter(
                    InetAddress.getLoopbackAddress().getHostAddress(), server.port());

            Object result = importer.call(11, 12, new Object[] {new LocalProxy(77), "payload"});

            assertThat(result).isEqualTo("remote result");
            server.await();
        }
    }

    private static String readHeaders(InputStream inputStream) throws IOException {
        String requestLine = readLine(inputStream);
        String line;
        do {
            line = readLine(inputStream);
        } while (!line.isEmpty());
        return requestLine;
    }

    private static String readLine(InputStream inputStream) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = inputStream.read()) >= 0) {
            if (value == '\r') {
                int next = inputStream.read();
                assertThat(next).isEqualTo('\n');
                return line.toString();
            }
            line.append((char) value);
        }
        throw new IOException("Unexpected end of stream while reading HTTP header");
    }

    private static void writeHttpHeader(OutputStream outputStream) throws IOException {
        outputStream.write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class RmiServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<?> future;

        private RmiServer(ServerSocket serverSocket, ExecutorService executorService, Future<?> future) {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
            this.future = future;
        }

        static RmiServer start(SocketHandler handler) throws IOException {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<?> future = executorService.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                    handler.handle(socket);
                }
                return null;
            });
            return new RmiServer(serverSocket, executorService, future);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void await() throws InterruptedException, ExecutionException, TimeoutException {
            future.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws IOException {
            try {
                serverSocket.close();
            } finally {
                executorService.shutdownNow();
            }
        }
    }

    private static final class IsolatedObjectImporterClassLoader extends ClassLoader {
        private IsolatedObjectImporterClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (ObjectImporter.class.getName().equals(name)) {
                        loadedClass = defineObjectImporterClass(name);
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineObjectImporterClass(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(className);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(className, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(className, exception);
            }
        }
    }

    private static final class LocalProxy implements Proxy {
        private final int objectId;

        private LocalProxy(int objectId) {
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }
    }

    public static final class RemoteGreeting implements Proxy {
        private final ObjectImporter importer;
        private final int objectId;

        public RemoteGreeting(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }
    }
}
