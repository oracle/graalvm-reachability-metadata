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

public class AbstractHttpInvokerRequestExecutorTest {
    @Test
    void executeRequestSerializesInvocationAndReadsSerializedResult() throws Exception {
        TestHttpInvokerRequestExecutor executor = new TestHttpInvokerRequestExecutor();
        HttpInvokerClientConfiguration config = new TestHttpInvokerClientConfiguration("https://example.test/remoting");
        RemoteInvocation invocation = new RemoteInvocation("greet", new Class<?>[] {String.class},
                new Object[] {"Spring"});

        RemoteInvocationResult result = executor.executeRequest(config, invocation);

        assertThat(executor.getSerializedInvocationSize()).isGreaterThan(0);
        assertThat(result.getValue()).isEqualTo("Hello Spring");
    }

    private static final class TestHttpInvokerRequestExecutor extends AbstractHttpInvokerRequestExecutor {
        private int serializedInvocationSize;

        @Override
        protected RemoteInvocationResult doExecuteRequest(
                HttpInvokerClientConfiguration config, ByteArrayOutputStream baos) throws Exception {
            assertThat(config.getServiceUrl()).isEqualTo("https://example.test/remoting");
            this.serializedInvocationSize = baos.size();
            RemoteInvocationResult response = new RemoteInvocationResult("Hello Spring");
            return readRemoteInvocationResult(new ByteArrayInputStream(serialize(response)), config.getCodebaseUrl());
        }

        int getSerializedInvocationSize() {
            return this.serializedInvocationSize;
        }

        private byte[] serialize(RemoteInvocationResult result) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
                objectOutputStream.writeObject(result);
            }
            return output.toByteArray();
        }
    }

    private static final class TestHttpInvokerClientConfiguration implements HttpInvokerClientConfiguration {
        private final String serviceUrl;

        private TestHttpInvokerClientConfiguration(String serviceUrl) {
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
