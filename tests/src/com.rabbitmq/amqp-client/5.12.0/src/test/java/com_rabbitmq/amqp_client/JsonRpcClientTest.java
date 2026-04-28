/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.tools.jsonrpc.DefaultJsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcClient;
import com.rabbitmq.tools.jsonrpc.JsonRpcException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRpcClientTest {
    @Test
    void createProxyBuildsInterfaceProxyBackedByJsonRpcCalls() throws Exception {
        StubJsonRpcClient client = new StubJsonRpcClient();
        GreetingService proxy = client.createProxy(GreetingService.class);

        String greeting = proxy.greet("metadata");

        assertThat(greeting).isEqualTo("Hello, metadata");
        assertThat(client.invocations).containsExactly(new RpcInvocation("greet", List.of("metadata")));
        assertThat(client.getServiceDescription().getProcedure("greet", 1).getName()).isEqualTo("greet");
    }

    public interface GreetingService {
        String greet(String name) throws IOException, JsonRpcException, TimeoutException;
    }

    private static class StubJsonRpcClient extends JsonRpcClient {
        private final List<RpcInvocation> invocations = new ArrayList<>();

        StubJsonRpcClient() throws IOException, JsonRpcException, TimeoutException {
            super(new RpcClientParams().exchange("json-rpc-exchange").routingKey("json-rpc-routing-key"),
                new DefaultJsonRpcMapper());
        }

        @Override
        protected DefaultConsumer setupConsumer() {
            return new DefaultConsumer(null);
        }

        @Override
        public Object call(String method, Object[] params) {
            if ("system.describe".equals(method)) {
                return Map.of(
                    "name", "Greeting service",
                    "id", "greeting-service",
                    "version", "1",
                    "summary", "Service used by JsonRpcClient proxy tests",
                    "help", "Invoke greet with one string argument",
                    "procs", List.of(Map.of(
                        "name", "greet",
                        "summary", "Create a greeting",
                        "help", "Returns a greeting for the supplied name",
                        "idempotent", true,
                        "return", "str",
                        "javaReturnType", String.class.getName(),
                        "params", List.of(Map.of(
                            "name", "name",
                            "type", "str"
                        ))
                    ))
                );
            }

            List<Object> actualParams = params == null ? List.of() : List.of(params);
            invocations.add(new RpcInvocation(method, actualParams));
            if ("greet".equals(method)) {
                return "Hello, " + actualParams.get(0);
            }
            throw new IllegalArgumentException("Unexpected JSON-RPC method: " + method);
        }
    }

    private static class RpcInvocation {
        private final String method;
        private final List<Object> params;

        RpcInvocation(String method, List<Object> params) {
            this.method = method;
            this.params = params;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RpcInvocation)) {
                return false;
            }
            RpcInvocation that = (RpcInvocation) other;
            return method.equals(that.method) && params.equals(that.params);
        }

        @Override
        public int hashCode() {
            return 31 * method.hashCode() + params.hashCode();
        }

        @Override
        public String toString() {
            return "RpcInvocation{" +
                "method='" + method + '\'' +
                ", params=" + params +
                '}';
        }
    }
}
