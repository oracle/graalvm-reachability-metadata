/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class LocalMailServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<?> result;
    private volatile Socket connection;

    LocalMailServer(Script script) throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        listener.setSoTimeout(10000);
        result = executor.submit(() -> {
            try (Socket socket = listener.accept()) {
                connection = socket;
                socket.setSoTimeout(10000);
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
                script.run(input, output);
            }
            return null;
        });
    }

    int port() {
        return listener.getLocalPort();
    }

    String host() {
        return listener.getInetAddress().getHostAddress();
    }

    void verify() throws Exception {
        result.get(10, TimeUnit.SECONDS);
    }

    static Properties properties(String protocol) {
        Properties properties = new Properties();
        properties.setProperty("mail." + protocol + ".connectiontimeout", "10000");
        properties.setProperty("mail." + protocol + ".timeout", "10000");
        return properties;
    }

    static void reply(BufferedWriter output, String response) throws Exception {
        output.write(response + "\r\n");
        output.flush();
    }

    @Override
    public void close() throws Exception {
        try {
            listener.close();
            Socket socket = connection;
            if (socket != null) {
                socket.close();
            }
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Mail server did not stop");
            }
        }
    }

    @FunctionalInterface
    interface Script {
        void run(BufferedReader input, BufferedWriter output) throws Exception;
    }
}
