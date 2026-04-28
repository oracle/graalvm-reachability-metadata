/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcServer;
import com.rabbitmq.tools.jsonrpc.DefaultJsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRpcServerTest {
    @Test
    void doCallInvokesMatchingServiceMethod() throws Exception {
        GreetingService service = new GreetingService();
        JsonRpcMapper mapper = new DefaultJsonRpcMapper();
        JsonRpcServer server = new DirectJsonRpcServer(Greetings.class, service, mapper);

        String responseBody = server.doCall("{\"id\":\"request-1\",\"version\":\"1.1\","
            + "\"method\":\"greet\",\"params\":[\"metadata\"]}");
        JsonRpcMapper.JsonRpcResponse response = mapper.parse(responseBody, String.class);

        assertThat(response.getResult()).isEqualTo("Hello, metadata");
        assertThat(response.getError()).isNull();
        assertThat(service.lastName).isEqualTo("metadata");
    }

    public interface Greetings {
        String greet(String name);
    }

    private static final class GreetingService implements Greetings {
        private String lastName;

        @Override
        public String greet(String name) {
            lastName = name;
            return "Hello, " + name;
        }
    }

    private static final class DirectJsonRpcServer extends JsonRpcServer {
        DirectJsonRpcServer(
            Class<?> interfaceClass,
            Object interfaceInstance,
            JsonRpcMapper mapper
        ) throws IOException {
            super((Channel) null, "direct-json-rpc", interfaceClass, interfaceInstance, mapper);
        }

        @Override
        protected RpcServer.RpcConsumer setupConsumer() {
            return null;
        }
    }
}
