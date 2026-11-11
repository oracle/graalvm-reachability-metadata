/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.client.BurlapProxyFactory;
import com.caucho.burlap.io.BurlapInput;
import com.caucho.burlap.io.BurlapOutput;
import com.caucho.burlap.server.BurlapSkeleton;
import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.server.HessianSkeleton;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RpcSupport implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RpcSupport() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/hessian", exchange -> handle(exchange, false));
        server.createContext("/burlap", exchange -> handle(exchange, true));
        server.start();
    }

    public String url(boolean burlap) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + (burlap ? "/burlap" : "/hessian");
    }

    public static HessianProxyFactory hessianFactory() {
        HessianProxyFactory factory = new HessianProxyFactory();
        factory.setConnectTimeout(20000);
        factory.setReadTimeout(20000);
        return factory;
    }

    public static BurlapProxyFactory burlapFactory() {
        return new BoundedBurlapFactory();
    }

    private void handle(HttpExchange exchange, boolean burlap) throws IOException {
        try {
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    if (burlap) {
                        BurlapOutput output = new BurlapOutput(response);
                        new BurlapSkeleton(new EchoService(), Echo.class).invoke(
                                new BurlapInput(exchange.getRequestBody()), output);
                        output.flush();
                    } else {
                        new HessianSkeleton(new EchoService(), Echo.class).invoke(exchange.getRequestBody(), response);
                    }
                } catch (Exception exception) {
                    throw new IOException("RPC request could not be processed", exception);
                }
            }
            byte[] bytes = response.toByteArray();
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public interface Echo {
        String echo(String text);
    }

    public static class EchoService implements Echo {
        @Override
        public String echo(String text) {
            return "reply:" + text;
        }
    }

    public static class BoundedBurlapFactory extends BurlapProxyFactory {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            URLConnection connection = super.openConnection(url);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            return connection;
        }
    }
}
