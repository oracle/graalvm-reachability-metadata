/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.razorvine.pickle.PythonException;
import net.razorvine.pyro.Config;
import net.razorvine.pyro.Message;
import net.razorvine.pyro.PyroException;
import net.razorvine.pyro.PyroProxy;
import net.razorvine.pyro.serializer.PyroSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PyroProxyTest {
    @Test
    void callCopiesPythonExceptionTracebackFromRemoteThrowable() throws Exception {
        Config.SerializerType originalSerializer = Config.SERIALIZER;
        boolean originalMetadata = Config.METADATA;
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = executor.submit(() -> servePythonExceptionResponse(serverSocket));
            Config.SERIALIZER = Config.SerializerType.pickle;
            Config.METADATA = true;

            PyroProxy proxy = new PyroProxy("127.0.0.1", serverSocket.getLocalPort(), "example.object");
            Throwable thrown = catchThrowable(() -> proxy.call("explode"));
            proxy.close();

            server.get(5, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOfSatisfying(PyroException.class, exception -> {
                assertThat(exception.pythonExceptionType).isEqualTo("builtins.ValueError");
                assertThat(exception._pyroTraceback).isEqualTo("remote traceback\n");
                assertThat(exception.getCause()).isInstanceOf(PythonException.class);
            });
        } finally {
            Config.SERIALIZER = originalSerializer;
            Config.METADATA = originalMetadata;
            executor.shutdownNow();
        }
    }

    private static void servePythonExceptionResponse(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            Message connect = Message.recv(socket.getInputStream(), new int[] {Message.MSG_CONNECT }, null);
            sendHandshakeResponse(socket, connect.seq);

            Message invoke = Message.recv(socket.getInputStream(), new int[] {Message.MSG_INVOKE }, null);
            byte[] remoteException = pythonValueErrorWithTraceback("remote failure", "remote traceback\n");
            Message result = new Message(
                    Message.MSG_RESULT,
                    remoteException,
                    Message.SERIALIZER_PICKLE,
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
        metadata.put("methods", new Object[] {"explode" });
        metadata.put("attrs", new Object[0]);
        metadata.put("oneways", new Object[0]);

        Map<String, Object> response = new HashMap<>();
        response.put("meta", metadata);
        byte[] data = PyroSerializer.getFor(Config.SerializerType.pickle).serializeData(response);
        Message message = new Message(
                Message.MSG_CONNECTOK,
                data,
                Message.SERIALIZER_PICKLE,
                Message.FLAGS_META_ON_CONNECT,
                sequenceNumber,
                null,
                null);
        send(socket, message);
    }

    private static void send(Socket socket, Message message) throws IOException {
        socket.getOutputStream().write(message.to_bytes());
        socket.getOutputStream().flush();
    }

    private static byte[] pythonValueErrorWithTraceback(String message, String traceback) throws IOException {
        ByteArrayOutputStream pickle = new ByteArrayOutputStream();
        pickle.write(0x80);
        pickle.write(0x02);
        pickle.write('c');
        pickle.write("builtins\nValueError\n".getBytes(StandardCharsets.UTF_8));
        writeBinUnicode(pickle, message);
        pickle.write(0x85);
        pickle.write('R');
        pickle.write('}');
        writeBinUnicode(pickle, "_pyroTraceback");
        writeBinUnicode(pickle, traceback);
        pickle.write('s');
        pickle.write('b');
        pickle.write('.');
        return pickle.toByteArray();
    }

    private static void writeBinUnicode(ByteArrayOutputStream pickle, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        pickle.write('X');
        pickle.write(bytes.length & 0xff);
        pickle.write((bytes.length >> 8) & 0xff);
        pickle.write((bytes.length >> 16) & 0xff);
        pickle.write((bytes.length >> 24) & 0xff);
        pickle.write(bytes);
    }
}
