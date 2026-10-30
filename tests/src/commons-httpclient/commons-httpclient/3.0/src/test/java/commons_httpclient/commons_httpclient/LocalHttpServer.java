/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;

final class LocalHttpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService executor;
    private final Future<String> request;

    LocalHttpServer(String response) throws IOException {
        listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        listener.setSoTimeout(10000);
        executor = Executors.newSingleThreadExecutor();
        request = executor.submit(() -> serve(response));
    }

    static LocalHttpServer ok(String body) throws IOException {
        return new LocalHttpServer("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                + "\r\nConnection: close\r\n\r\n" + body);
    }

    static HttpClient client() {
        HttpClient client = new HttpClient();
        client.getHttpConnectionManager().getParams().setConnectionTimeout(10000);
        client.getHttpConnectionManager().getParams().setSoTimeout(10000);
        client.getParams().setConnectionManagerTimeout(10000);
        return client;
    }

    int port() {
        return listener.getLocalPort();
    }

    String url(String path) {
        return "http://127.0.0.1:" + port() + path;
    }

    String request() throws Exception {
        return request.get(20, TimeUnit.SECONDS);
    }

    private String serve(String response) throws IOException {
        try (Socket socket = listener.accept()) {
            socket.setSoTimeout(10000);
            InputStream input = socket.getInputStream();
            StringBuilder headers = new StringBuilder();
            String line = readLine(input);
            headers.append(line).append("\r\n");
            int length = 0;
            while (!(line = readLine(input)).isEmpty()) {
                headers.append(line).append("\r\n");
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
                if (lower.startsWith("expect:") && lower.contains("100-continue")) {
                    socket.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                }
            }
            byte[] body = input.readNBytes(length);
            if (body.length != length) {
                throw new EOFException("Incomplete request body");
            }
            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return headers.append("\r\n").append(new String(body, StandardCharsets.UTF_8)).toString();
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                return bytes.toString(StandardCharsets.US_ASCII).replace("\r", "");
            }
            bytes.write(value);
        }
        throw new EOFException("Incomplete request headers");
    }

    @Override
    public void close() throws Exception {
        try {
            listener.close();
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("HTTP server did not stop");
            }
        }
    }
}
