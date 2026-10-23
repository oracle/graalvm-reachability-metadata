/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.tools.jsonrpc.JacksonJsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcClient;
import com.rabbitmq.tools.jsonrpc.JsonRpcException;
import com.rabbitmq.tools.jsonrpc.JsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;
import com.rabbitmq.tools.jsonrpc.ProcedureDescription;
import com.rabbitmq.tools.jsonrpc.ServiceDescription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/// Exposes a Java service interface as JSON-RPC over AMQP with `JsonRpcServer`
/// and `JsonRpcClient`, mapping calls through Jackson with `JacksonJsonRpcMapper`.
///
/// The scenario is an inventory service: the server publishes an interface on a
/// named queue, and a client discovers it through `system.describe`, calls it
/// through a typed proxy and by procedure name, and observes service errors.
public class JsonRpcServiceTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final int CALL_TIMEOUT_MILLIS = 10_000;

    private static String containerId;

    private static ConnectionFactory factory;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5672", IMAGE);
        String mapping = docker("port", containerId, "5672/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(false);
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void typedProxyCallsTheRemoteInventory() throws Exception {
        try (Connection connection = factory.newConnection("json-rpc-inventory")) {
            String queue = "rpc.inventory";
            Channel serverChannel = connection.createChannel();
            serverChannel.queueDeclare(queue, true, false, true, null);
            InMemoryInventory inventory = new InMemoryInventory();
            inventory.stock.put("sku-1", 10);
            JsonRpcMapper mapper = new JacksonJsonRpcMapper(new ObjectMapper());
            JsonRpcServer server = new JsonRpcServer(serverChannel, queue, Inventory.class, inventory, mapper);
            CompletableFuture<ShutdownSignalException> serverLoop = serve(server);

            ServiceDescription served = server.getServiceDescription();
            assertThat(procedureNames(served)).contains("greet", "reserve", "inStock", "quote", "restock");

            Channel clientChannel = connection.createChannel();
            JsonRpcClient client = new JsonRpcClient(new RpcClientParams()
                    .channel(clientChannel)
                    .exchange("")
                    .routingKey(queue)
                    .timeout(CALL_TIMEOUT_MILLIS), mapper);
            ServiceDescription discovered = client.getServiceDescription();
            assertThat(procedureNames(discovered)).isEqualTo(procedureNames(served));
            ProcedureDescription reserve = discovered.getProcedure("reserve", 2);
            assertThat(reserve.arity()).isEqualTo(2);
            assertThat(reserve.getReturnType()).isEqualTo(int.class);

            Inventory remote = client.createProxy(Inventory.class);
            assertThat(remote.greet("warehouse")).isEqualTo("inventory ready for warehouse");
            assertThat(remote.inStock("sku-1")).isTrue();
            assertThat(remote.inStock("sku-2")).isFalse();
            assertThat(remote.reserve("sku-1", 3)).isEqualTo(7);
            assertThat(remote.quote("sku-1", 4)).isEqualTo(9.98);
            assertThat(remote.restock("sku-2", 5L)).isEqualTo(5L);
            assertThat(remote.inStock("sku-2")).isTrue();

            assertThat(inventory.stock).containsEntry("sku-1", 7).containsEntry("sku-2", 5);

            client.close();
            stop(server, serverLoop);
        }
    }

    @Test
    void callsByNameReportServiceErrorsToTheCaller() throws Exception {
        try (Connection connection = factory.newConnection("json-rpc-errors")) {
            Channel serverChannel = connection.createChannel();
            String queue = serverChannel.queueDeclare().getQueue();
            InMemoryInventory inventory = new InMemoryInventory();
            inventory.stock.put("sku-9", 1);
            JsonRpcMapper mapper = new JacksonJsonRpcMapper();
            JsonRpcServer server = new JsonRpcServer(serverChannel, queue, Inventory.class, inventory, mapper);
            CompletableFuture<ShutdownSignalException> serverLoop = serve(server);

            Channel clientChannel = connection.createChannel();
            JsonRpcClient client = new JsonRpcClient(clientChannel, "", queue, CALL_TIMEOUT_MILLIS, mapper);

            assertThat(client.call("reserve", new Object[] {"sku-9", 1})).isEqualTo(0);
            assertThat(client.call("greet", new Object[] {"night shift"}))
                    .isEqualTo("inventory ready for night shift");

            JsonRpcException refused = catchThrowableOfType(
                    () -> client.call("reserve", new Object[] {"sku-9", 1}), JsonRpcException.class);
            assertThat(refused).isNotNull();
            Map<?, ?> reply = (Map<?, ?>) refused.getError();
            assertThat(reply.get("version")).isEqualTo("1.1");
            Map<?, ?> fault = (Map<?, ?>) reply.get("error");
            assertThat(fault.get("name")).isEqualTo("JSONRPCError");
            assertThat(fault.get("code")).isEqualTo(500);
            assertThat(fault.get("message")).isEqualTo("Internal Server Error");
            assertThat(String.valueOf(fault.get("error"))).contains("only 0 of sku-9 left");
            assertThat(inventory.stock).containsEntry("sku-9", 0);

            client.close();
            stop(server, serverLoop);
        }
    }

    /// The service contract the server exposes and the client proxies.
    public interface Inventory {

        String greet(String caller);

        boolean inStock(String sku);

        int reserve(String sku, int quantity);

        double quote(String sku, int quantity);

        long restock(String sku, long quantity);
    }

    /// Keeps stock levels in memory and refuses reservations it cannot cover.
    public static final class InMemoryInventory implements Inventory {

        private final Map<String, Integer> stock = new ConcurrentHashMap<>();

        @Override
        public String greet(String caller) {
            return "inventory ready for " + caller;
        }

        @Override
        public boolean inStock(String sku) {
            return stock.getOrDefault(sku, 0) > 0;
        }

        @Override
        public int reserve(String sku, int quantity) {
            int available = stock.getOrDefault(sku, 0);
            if (available < quantity) {
                throw new IllegalStateException("only " + available + " of " + sku + " left");
            }
            stock.put(sku, available - quantity);
            return available - quantity;
        }

        @Override
        public double quote(String sku, int quantity) {
            return quantity * 2.495;
        }

        @Override
        public long restock(String sku, long quantity) {
            return stock.merge(sku, (int) quantity, Integer::sum);
        }
    }

    private static List<String> procedureNames(ServiceDescription description) {
        return description.getProcs().stream()
                .map(ProcedureDescription::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private static CompletableFuture<ShutdownSignalException> serve(JsonRpcServer server) {
        CompletableFuture<ShutdownSignalException> loop = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                loop.complete(server.mainloop());
            } catch (Throwable e) {
                loop.completeExceptionally(e);
            }
        }, "json-rpc-server");
        thread.setDaemon(true);
        thread.start();
        return loop;
    }

    /// Stops the server the way an application shuts a service down: by closing its channel.
    private static void stop(JsonRpcServer server, CompletableFuture<ShutdownSignalException> loop)
            throws Exception {
        server.terminateMainloop();
        server.getChannel().close();
        ShutdownSignalException signal = loop.get(10, TimeUnit.SECONDS);
        assertThat(signal).isNotNull();
        assertThat(signal.isInitiatedByApplication()).isTrue();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = factory.newConnection("startup-probe")) {
                if (connection.isOpen()) {
                    return;
                }
            } catch (IOException | TimeoutException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("RabbitMQ did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
