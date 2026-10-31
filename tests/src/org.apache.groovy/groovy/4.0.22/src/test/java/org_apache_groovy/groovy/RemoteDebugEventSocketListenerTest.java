/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.runtime.RecognitionException;
import groovyjarjarantlr4.runtime.debug.BlankDebugEventListener;
import groovyjarjarantlr4.runtime.debug.RemoteDebugEventSocketListener;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RemoteDebugEventSocketListenerTest {
    @Test
    void reconstructsARecognitionExceptionReceivedOverTheDebugProtocol() throws Exception {
        AtomicReference<RecognitionException> received = new AtomicReference<>();
        BlankDebugEventListener events = new BlankDebugEventListener() {
            @Override
            public void recognitionException(RecognitionException exception) {
                received.set(exception);
            }
        };
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10000);
            RemoteDebugEventSocketListener client = new RemoteDebugEventSocketListener(events,
                    server.getInetAddress().getHostAddress(), server.getLocalPort());
            try (Socket socket = server.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                socket.setSoTimeout(10000);
                Future<?> processing = executor.submit(client);
                output.println("ANTLR\t2");
                output.println("grammar\t\"Labels.g");
                assertEquals("ack", input.readLine());
                output.println("exception\t" + RecognitionException.class.getName() + "\t5\t3\t7");
                assertEquals("ack", input.readLine());
                socket.shutdownOutput();
                processing.get(20, TimeUnit.SECONDS);
            }
        }
        RecognitionException exception = received.get();
        assertNotNull(exception);
        assertEquals(5, exception.index);
        assertEquals(3, exception.line);
        assertEquals(7, exception.charPositionInLine);
    }
}
