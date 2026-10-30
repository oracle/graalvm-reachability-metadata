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
import java.util.concurrent.TimeUnit;

public final class RpcEndpoint implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private RpcEndpoint(boolean hessian) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HessianSkeleton hessianSkeleton = new HessianSkeleton(new GreetingService(), Greeting.class);
        BurlapSkeleton burlapSkeleton = new BurlapSkeleton(new GreetingService(), Greeting.class);
        server.createContext("/rpc", exchange -> serve(exchange, hessian, hessianSkeleton, burlapSkeleton));
        server.setExecutor(executor);
        server.start();
    }

    static RpcEndpoint hessian() throws IOException {
        return new RpcEndpoint(true);
    }

    static RpcEndpoint burlap() throws IOException {
        return new RpcEndpoint(false);
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/rpc";
    }

    private void serve(HttpExchange exchange, boolean hessian, HessianSkeleton hessianSkeleton, BurlapSkeleton burlapSkeleton) throws IOException {
        try (exchange) {
            if (exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (hessian) {
                hessianSkeleton.invoke(exchange.getRequestBody(), bytes);
            } else {
                BurlapOutput output = new BurlapOutput(bytes);
                burlapSkeleton.invoke(new BurlapInput(exchange.getRequestBody()), output);
                output.flush();
            }
            byte[] response = bytes.toByteArray();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (Exception e) {
            throw new IOException("RPC dispatch failed", e);
        }
    }

    @Override
    public void close() throws InterruptedException {
        server.stop(0);
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("RPC executor did not terminate");
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    static class BoundedBurlapFactory extends BurlapProxyFactory {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            URLConnection connection = super.openConnection(url);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            return connection;
        }
    }
}
