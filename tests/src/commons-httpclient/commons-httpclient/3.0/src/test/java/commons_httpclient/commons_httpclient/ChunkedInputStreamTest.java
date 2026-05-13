/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.ChunkedInputStream;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;

public class ChunkedInputStreamTest {
    @Test
    void httpClientReadsChunkedResponseAndTrailerHeaders() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<Void> serverResponse = new FutureTask<>(
                    () -> writeChunkedResponseToAcceptedSocket(serverSocket));
            Thread serverThread = new Thread(serverResponse, "commons-httpclient-chunked-server");
            serverThread.start();

            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
            client.getHttpConnectionManager().getParams().setSoTimeout(5000);
            GetMethod method = new GetMethod("http://" + loopbackAddress.getHostAddress()
                    + ":" + serverSocket.getLocalPort() + "/chunked");

            try {
                int statusCode = client.executeMethod(method);

                assertThat(statusCode).isEqualTo(HttpStatus.SC_OK);
                assertThat(method.getResponseBodyAsString()).isEqualTo("Apache HttpClient");
                assertThat(method.getResponseFooters())
                        .extracting(Header::getName, Header::getValue)
                        .containsExactly(tuple("X-Chunk-Trailer", "complete"));
                serverResponse.get(5, TimeUnit.SECONDS);
            } finally {
                method.releaseConnection();
                serverSocket.close();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    @Test
    void readsChunkedBodyAndLeavesFollowingResponseBytesAvailable() throws Exception {
        String rawResponseText = "4\r\n"
                + "Wiki\r\n"
                + "5; extension=\"quoted value\"\r\n"
                + "pedia\r\n"
                + "0\r\n"
                + "X-Trailer: done\r\n"
                + "\r\n"
                + "NEXT";
        byte[] rawResponse = rawResponseText.getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream wireInput = new ByteArrayInputStream(rawResponse);

        StringBuilder body = new StringBuilder();
        try (ChunkedInputStream chunkedInput = new ChunkedInputStream(wireInput)) {
            int value = chunkedInput.read();
            while (value != -1) {
                body.append((char) value);
                value = chunkedInput.read();
            }
        }

        assertThat(body).hasToString("Wikipedia");
        assertThat(wireInput.readAllBytes()).isEqualTo("NEXT".getBytes(StandardCharsets.US_ASCII));
    }

    private static Void writeChunkedResponseToAcceptedSocket(
            ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5000);
            readRequestHeaders(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + "6\r\n"
                    + "Apache\r\n"
                    + "B\r\n"
                    + " HttpClient\r\n"
                    + "0\r\n"
                    + "X-Chunk-Trailer: complete\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return null;
        }
    }

    private static void readRequestHeaders(InputStream input) throws Exception {
        int previous = -1;
        int current = -1;
        int matched = 0;
        while (matched < 4) {
            previous = current;
            current = input.read();
            if (current == -1) {
                throw new AssertionError("HTTP request ended before headers were complete");
            }
            if ((matched == 0 && current == '\r')
                    || (matched == 1 && current == '\n')
                    || (matched == 2 && previous == '\n' && current == '\r')
                    || (matched == 3 && current == '\n')) {
                matched++;
            } else {
                matched = current == '\r' ? 1 : 0;
            }
        }
    }
}
