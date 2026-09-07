/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import org.springframework.remoting.httpinvoker.AbstractHttpInvokerRequestExecutor;
import org.springframework.remoting.httpinvoker.HttpInvokerClientConfiguration;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationResult;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class AbstractHttpInvokerRequestExecutorTest {

    @Test
    void executeRequestSerializesInvocationAndReadsInvocationResult() throws Exception {
        LoopbackHttpInvokerRequestExecutor executor = new LoopbackHttpInvokerRequestExecutor();
        executor.setBeanClassLoader(getClass().getClassLoader());
        RemoteInvocation invocation = new RemoteInvocation(
                "greet", new Class<?>[] {String.class}, new Object[] {"spring"});

        RemoteInvocationResult result = executor.executeRequest(
                new StaticHttpInvokerClientConfiguration("https://example.test/remoting"), invocation);

        assertThat(executor.requestBodySize).isGreaterThan(0);
        assertThat(result.hasException()).isFalse();
        assertThat(result.getValue()).isEqualTo("greet spring");
    }

    private static final class LoopbackHttpInvokerRequestExecutor extends AbstractHttpInvokerRequestExecutor {

        private int requestBodySize;

        @Override
        protected RemoteInvocationResult doExecuteRequest(
                HttpInvokerClientConfiguration config, ByteArrayOutputStream baos) throws Exception {

            this.requestBodySize = baos.size();
            RemoteInvocationResult response = new RemoteInvocationResult("greet spring");
            return readRemoteInvocationResult(serialize(response), config.getCodebaseUrl());
        }

        private static ByteArrayInputStream serialize(RemoteInvocationResult response) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
                objectOutputStream.writeObject(response);
            }
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private static final class StaticHttpInvokerClientConfiguration implements HttpInvokerClientConfiguration {

        private final String serviceUrl;

        private StaticHttpInvokerClientConfiguration(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        @Override
        public String getServiceUrl() {
            return this.serviceUrl;
        }

        @Override
        public String getCodebaseUrl() {
            return null;
        }
    }
}
