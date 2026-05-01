/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.razorvine.pyro.Message;
import net.razorvine.pyro.PyroException;
import net.razorvine.pyro.PyroProxy;
import net.razorvine.pyro.serializer.PyroSerializer;
import net.razorvine.pyro.serializer.SerpentSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PyroProxyTest {
    private static final byte[] REMOTE_THROWABLE_PAYLOAD = new byte[] {82, 84};

    @Test
    void callCopiesPythonExceptionTracebackFromRemoteThrowable() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = executor.submit(() -> servePythonExceptionResponse(serverSocket));

            PyroProxy proxy = new PyroProxy("127.0.0.1", serverSocket.getLocalPort(), "example.object");
            Throwable thrown = catchThrowable(() -> proxy.call("explode"));
            proxy.close();

            server.get(5, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOfSatisfying(PyroException.class, exception -> {
                assertThat(exception).hasMessage("[builtins.ValueError] remote failure");
                assertThat(exception.pythonExceptionType).isEqualTo("builtins.ValueError");
                assertThat(exception._pyroTraceback).isEqualTo("remote traceback\n");
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void callCopiesTracebackFromNonPyroRemoteThrowable() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SerpentSerializer previousSerializer = TracebackSerializer.install();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = executor.submit(() -> serveNonPyroThrowableResponse(serverSocket));

            PyroProxy proxy = new PyroProxy("127.0.0.1", serverSocket.getLocalPort(), "example.object");
            Throwable thrown = catchThrowable(() -> proxy.call("explode"));
            proxy.close();

            server.get(5, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOfSatisfying(PyroException.class, exception -> {
                assertThat(exception.getCause()).isInstanceOf(RemoteTracebackException.class);
                assertThat(exception._pyroTraceback).isEqualTo("remote throwable traceback\n");
            });
        } finally {
            TracebackSerializer.restore(previousSerializer);
            executor.shutdownNow();
        }
    }

    private static void servePythonExceptionResponse(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            Message connect = Message.recv(socket.getInputStream(), new int[] {Message.MSG_CONNECT});
            sendHandshakeResponse(socket, connect.seq);

            Message invoke = Message.recv(socket.getInputStream(), new int[] {Message.MSG_INVOKE});
            byte[] remoteException = pythonValueErrorWithTraceback("remote failure", "remote traceback\n");
            Message result = new Message(
                    Message.MSG_RESULT,
                    remoteException,
                    Message.SERIALIZER_SERPENT,
                    Message.FLAGS_EXCEPTION,
                    invoke.seq,
                    null,
                    null);
            send(socket, result);
        } catch (IOException exception) {
            throw new AssertionError("Pyro test server failed", exception);
        }
    }

    private static void sendHandshakeResponse(Socket socket, int sequenceNumber) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("methods", new Object[] {"explode"});
        metadata.put("attrs", new Object[0]);
        metadata.put("oneways", new Object[0]);

        Map<String, Object> response = new HashMap<>();
        response.put("meta", metadata);
        response.put("handshake", "accepted");
        byte[] data = PyroSerializer.getSerpentSerializer().serializeData(response);
        Message message = new Message(
                Message.MSG_CONNECTOK,
                data,
                Message.SERIALIZER_SERPENT,
                0,
                sequenceNumber,
                null,
                null);
        send(socket, message);
    }

    private static void serveNonPyroThrowableResponse(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            Message connect = Message.recv(socket.getInputStream(), new int[] {Message.MSG_CONNECT});
            sendHandshakeResponse(socket, connect.seq);

            Message invoke = Message.recv(socket.getInputStream(), new int[] {Message.MSG_INVOKE});
            Message result = new Message(
                    Message.MSG_RESULT,
                    REMOTE_THROWABLE_PAYLOAD,
                    Message.SERIALIZER_SERPENT,
                    Message.FLAGS_EXCEPTION,
                    invoke.seq,
                    null,
                    null);
            send(socket, result);
        } catch (IOException exception) {
            throw new AssertionError("Pyro test server failed", exception);
        }
    }

    private static void send(Socket socket, Message message) throws IOException {
        socket.getOutputStream().write(message.to_bytes());
        socket.getOutputStream().flush();
    }

    private static byte[] pythonValueErrorWithTraceback(String message, String traceback) throws IOException {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("_pyroTraceback", traceback);

        Map<String, Object> exception = new HashMap<>();
        exception.put("__class__", "builtins.ValueError");
        exception.put("__exception__", true);
        exception.put("args", new Object[] {message});
        exception.put("attributes", attributes);
        return PyroSerializer.getSerpentSerializer().serializeData(exception);
    }

    public static final class RemoteTracebackException extends RuntimeException {
        public final String _pyroTraceback;

        RemoteTracebackException(String message, String traceback) {
            super(message);
            this._pyroTraceback = traceback;
        }
    }

    private static final class TracebackSerializer extends SerpentSerializer {
        private final SerpentSerializer delegate;

        TracebackSerializer(SerpentSerializer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object deserializeData(byte[] data) throws IOException {
            if (Arrays.equals(data, REMOTE_THROWABLE_PAYLOAD)) {
                return new RemoteTracebackException("remote throwable failure", "remote throwable traceback\n");
            }
            return delegate.deserializeData(data);
        }

        static SerpentSerializer install() {
            SerpentSerializer previousSerializer = serpentSerializer;
            SerpentSerializer delegate = previousSerializer == null ? new SerpentSerializer() : previousSerializer;
            serpentSerializer = new TracebackSerializer(delegate);
            return previousSerializer;
        }

        static void restore(SerpentSerializer previousSerializer) {
            serpentSerializer = previousSerializer;
        }
    }
}
