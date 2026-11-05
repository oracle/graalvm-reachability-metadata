/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.xbill.DNS.ARecord;
import com.couchbase.client.core.deps.org.xbill.DNS.DClass;
import com.couchbase.client.core.deps.org.xbill.DNS.Flags;
import com.couchbase.client.core.deps.org.xbill.DNS.Message;
import com.couchbase.client.core.deps.org.xbill.DNS.Section;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class LocalDohServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    LocalDohServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dns-query", this::answer);
        server.setExecutor(executor);
        server.start();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/dns-query";
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] wire;
            if (exchange.getRequestMethod().equals("POST")) {
                wire = exchange.getRequestBody().readAllBytes();
            } else {
                String query = exchange.getRequestURI().getRawQuery();
                wire = Base64.getUrlDecoder().decode(query.substring("dns=".length()));
            }
            Message request = new Message(wire);
            Message response = new Message(request.getHeader().getID());
            response.getHeader().setFlag(Flags.QR);
            response.getHeader().setFlag(Flags.AA);
            response.addRecord(request.getQuestion(), Section.QUESTION);
            response.addRecord(new ARecord(request.getQuestion().getName(), DClass.IN, 60,
                    InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 42})), Section.ANSWER);
            byte[] body = response.toWire();
            exchange.getResponseHeaders().set("Content-Type", "application/dns-message");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    @Override
    public void close() throws InterruptedException {
        server.stop(0);
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Local DNS server did not terminate");
        }
    }
}
